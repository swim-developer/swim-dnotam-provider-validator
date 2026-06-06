package com.github.swim_developer.validator.dnotam.provider.infrastructure.messaging;

import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConsoleNotificationPort;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.MessagePersistencePort;
import com.github.swim_developer.validator.provider.domain.model.Subscription;
import com.github.swim_developer.validator.provider.domain.port.out.SubscriptionRepository;
import com.github.swim_developer.validator.provider.infrastructure.messaging.UserConnectionState;
import io.vertx.amqp.AmqpReceiver;
import io.vertx.amqp.AmqpReceiverOptions;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@ApplicationScoped
public class UserReceiverLifecycle {

    private final Vertx vertx;
    private final ConsoleNotificationPort consoleService;
    private final MessagePersistencePort messageService;
    private final SubscriptionRepository subscriptionRepository;

    @Inject
    public UserReceiverLifecycle(
            Vertx vertx,
            ConsoleNotificationPort consoleService,
            MessagePersistencePort messageService,
            SubscriptionRepository subscriptionRepository) {
        this.vertx = vertx;
        this.consoleService = consoleService;
        this.messageService = messageService;
        this.subscriptionRepository = subscriptionRepository;
    }

    public void createReceiver(String userId, String subscriptionId, String queueName, UserConnectionState state) {
        if (state == null || !state.isConnected()) {
            log.warn("Cannot create receiver - user {} not connected", userId);
            return;
        }

        if (state.hasReceiver(queueName)) {
            log.info("Receiver already exists for queue: {} (user: {})", queueName, state.getUsername());
            return;
        }

        log.info("Creating receiver for queue: {} (user: {})", queueName, state.getUsername());
        consoleService.info(userId, "Creating receiver for queue: " + queueName);

        AmqpReceiverOptions options = new AmqpReceiverOptions().setAutoAcknowledgement(false);

        state.getConnection().createReceiver(queueName, options)
                .onSuccess(receiver -> {
                    state.addReceiver(queueName, receiver);
                    log.info("Receiver created for queue: {} (user: {})", queueName, state.getUsername());
                    consoleService.success(userId, "Listening on queue: " + queueName);

                    receiver.handler(message -> {
                        String messageId = message.id() != null ? message.id() : UUID.randomUUID().toString();
                        String contentType = message.contentType();
                        String body = message.bodyAsString();

                        log.info("Message received on queue {} for user {}: {}", queueName, state.getUsername(), messageId);
                        consoleService.info(userId, "Message received: " + messageId);

                        vertx.getOrCreateContext().executeBlocking(
                                () -> persistMessage(userId, subscriptionId, queueName, messageId, contentType, body), true)
                            .onSuccess(v -> {
                                message.accepted();
                                log.info("Message acknowledged: {}", messageId);
                            }).onFailure(e -> {
                                message.rejected();
                                log.error("Failed to save message, rejected: {}", messageId, e);
                                consoleService.error(userId, "Failed to save message: " + e.getMessage());
                            });
                    });

                    receiver.exceptionHandler(err -> {
                        log.error("Receiver error on queue {} (user: {}): {}", queueName, state.getUsername(), err.getMessage());
                        consoleService.error(userId, "Receiver error on " + queueName + ": " + err.getMessage());
                    });

                    receiver.endHandler(v -> {
                        log.info("Receiver ended for queue: {} (user: {})", queueName, state.getUsername());
                        state.removeReceiver(queueName);
                        consoleService.warning(userId, "Receiver ended for queue: " + queueName);
                    });
                })
                .onFailure(err -> {
                    log.error("Failed to create receiver for queue {} (user: {}): {}", queueName, state.getUsername(), err.getMessage());
                    consoleService.error(userId, "Failed to create receiver for " + queueName + ": " + err.getMessage());
                });
    }

    public void createReceiversForUser(String userId, UserConnectionState state) {
        if (state == null || !state.isConnected()) {
            log.warn("Cannot create receivers - user {} not connected", userId);
            return;
        }

        vertx.getOrCreateContext().executeBlocking(
                () -> getActiveSubscriptionsForUser(state.getUsername()), true)
                .onSuccess(subscriptions -> {
                    log.info("Creating receivers for {} subscriptions (user: {})", subscriptions.size(), state.getUsername());
                    for (Subscription sub : subscriptions) {
                        if (sub.getQueueName() != null && !sub.getQueueName().isBlank()) {
                            createReceiver(userId, sub.getSubscriptionId(), sub.getQueueName(), state);
                        }
                    }
                })
                .onFailure(err -> log.error("Failed to load subscriptions for user: {}", state.getUsername(), err));
    }

    public void testQueueAccess(String userId, String queueName, UserConnectionState state,
            CompletableFuture<String> future) {
        if (state == null || !state.isConnected()) {
            future.complete("Not connected to AMQP broker after reconnect");
            return;
        }

        log.info("Testing access to queue: {} (user: {})", queueName, state.getUsername());
        consoleService.info(userId, "Testing access to queue: " + queueName);

        state.getConnection().createReceiver(queueName)
                .onSuccess(receiver -> {
                    log.info("Access granted to queue: {} (user: {})", queueName, state.getUsername());
                    consoleService.success(userId, "Access granted to queue: " + queueName);
                    receiver.close();
                    future.complete(null);
                })
                .onFailure(err -> {
                    log.warn("Access denied to queue {} (user: {}): {}", queueName, state.getUsername(), err.getMessage());
                    consoleService.warning(userId, "Access denied to queue " + queueName + ": " + err.getMessage());
                    future.complete(err.getMessage());
                });
    }

    public void closeReceiver(String userId, String queueName, UserConnectionState state) {
        if (state != null && state.hasReceiver(queueName)) {
            AmqpReceiver receiver = state.removeReceiver(queueName);
            if (receiver != null) {
                receiver.close();
                log.info("Closed receiver for queue: {} (user: {})", queueName, state.getUsername());
                consoleService.info(userId, "Stopped listening on queue: " + queueName);
            }
        }
    }

    @Transactional
    public List<Subscription> getActiveSubscriptionsForUser(String username) {
        return subscriptionRepository.findByUsername(username).stream()
                .filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus()))
                .toList();
    }

    private Void persistMessage(String userId, String subscriptionId, String queueName,
            String messageId, String contentType, String body) {
        messageService.saveMessage(userId, subscriptionId, queueName, messageId, contentType, body);
        return null;
    }
}
