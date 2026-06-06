package com.github.swim_developer.validator.dnotam.provider.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.swim_developer.validator.provider.domain.model.Subscription;
import com.github.swim_developer.validator.provider.domain.port.out.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConnectionTrackerPort;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ProviderSubscriptionPort;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConsoleNotificationPort;

@Slf4j
@ApplicationScoped
public class SubscriptionService implements ProviderSubscriptionPort {

    private static final String STATUS_DELETED = "DELETED";

    private final ObjectMapper objectMapper;
    private final ConsoleNotificationPort consoleService;
    private final ConnectionTrackerPort connectionManager;
    private final SubscriptionRepository subscriptionRepository;

    @Inject
    public SubscriptionService(
            ObjectMapper objectMapper,
            ConsoleNotificationPort consoleService,
            ConnectionTrackerPort connectionManager,
            SubscriptionRepository subscriptionRepository) {
        this.objectMapper = objectMapper;
        this.consoleService = consoleService;
        this.connectionManager = connectionManager;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public void createSubscription(String responseBody, String userId, String username, String providerUrl, String requestBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            
            String subscriptionId = extractField(json, "subscription_id", "subscriptionId", "id");
            if (subscriptionId == null) {
                log.warn("No subscription ID found in response");
                return;
            }

            if (subscriptionRepository.findBySubscriptionId(subscriptionId).isPresent()) {
                log.info("Subscription already exists: {}", subscriptionId);
                return;
            }

            String queueName = extractField(json, "queue", "queueName");
            String topicId = extractField(json, "topic", "topicId");
            String status = extractField(json, "subscription_status", "subscriptionStatus", "status");

            Subscription subscription = new Subscription();
            subscription.setSubscriptionId(subscriptionId);
            subscription.setTopicId(topicId);
            subscription.setQueueName(queueName);
            subscription.setStatus(status);
            subscription.setUsername(username);
            subscription.setProviderUrl(providerUrl);
            subscription.setRequestBody(requestBody);
            subscription.setCreatedAt(LocalDateTime.now());
            subscription.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.insert(subscription);

            log.info("Subscription created: {} queue={} for user {}", subscriptionId, queueName, username);
            consoleService.success(userId, "Subscription created: " + subscriptionId);

            if (connectionManager.isConnected(userId) && queueName != null) {
                connectionManager.createReceiver(userId, subscriptionId, queueName);
                consoleService.info(userId, "Receiver created for queue: " + queueName);
            } else {
                consoleService.info(userId, "Go to AMQP page to connect and start receiving messages");
            }
        } catch (Exception e) {
            log.error("Failed to create subscription", e);
            consoleService.error(userId, "Failed to create subscription: " + e.getMessage());
        }
    }

    @Transactional
    public void updateSubscription(String responseBody, String userId, String subscriptionId) {
        try {
            Subscription subscription = subscriptionRepository.findBySubscriptionId(subscriptionId).orElse(null);
            if (subscription == null) {
                log.warn("Subscription not found for update: {}", subscriptionId);
                return;
            }

            JsonNode json = responseBody != null && !responseBody.isBlank() ? objectMapper.readTree(responseBody) : null;
            String newStatus = json != null ? extractField(json, "subscription_status", "subscriptionStatus", "status") : null;
            
            if (newStatus != null) {
                subscription.setStatus(newStatus);
                subscription.setUpdatedAt(LocalDateTime.now());
                subscriptionRepository.update(subscription);

                log.info("Subscription updated: {} status={}", subscriptionId, newStatus);
                consoleService.info(userId, "Subscription updated: " + subscriptionId + " → " + newStatus);

                if ("ACTIVE".equalsIgnoreCase(newStatus)) {
                    if (connectionManager.isConnected(userId) && subscription.getQueueName() != null) {
                        connectionManager.createReceiver(userId, subscriptionId, subscription.getQueueName());
                        consoleService.info(userId, "Receiver created for queue: " + subscription.getQueueName());
                    }
                } else if ("PAUSED".equalsIgnoreCase(newStatus) || STATUS_DELETED.equalsIgnoreCase(newStatus)) {
                    closeReceiverIfExists(userId, subscription.getQueueName());
                }
            }
        } catch (Exception e) {
            log.error("Failed to update subscription", e);
            consoleService.error(userId, "Failed to update subscription: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteSubscription(String userId, String subscriptionId) {
        Subscription subscription = subscriptionRepository.findBySubscriptionId(subscriptionId).orElse(null);
        if (subscription != null) {
            closeReceiverIfExists(userId, subscription.getQueueName());
            subscription.setStatus(STATUS_DELETED);
            subscription.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.update(subscription);
            log.info("Subscription marked as {}: {}", STATUS_DELETED, subscriptionId);
            consoleService.info(userId, "Subscription deleted: " + subscriptionId);
        } else {
            log.warn("Subscription not found for delete: {}", subscriptionId);
        }
    }

    private void closeReceiverIfExists(String userId, String queueName) {
        if (queueName != null) {
            connectionManager.closeReceiverForQueue(userId, queueName);
        }
    }

    private String extractField(JsonNode json, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (json.has(fieldName) && !json.get(fieldName).isNull()) {
                return json.get(fieldName).asText();
            }
        }
        return null;
    }

    public List<Subscription> getSubscriptionsByUsername(String username) {
        return subscriptionRepository.findByUsername(username);
    }

    public List<Subscription> getActiveSubscriptionsByUsername(String username) {
        return subscriptionRepository.findByUsername(username).stream()
            .filter(s -> !STATUS_DELETED.equalsIgnoreCase(s.getStatus()))
            .toList();
    }

    public long countActiveSubscriptionsByUsername(String username) {
        return subscriptionRepository.countByUsernameAndStatusNot(username, STATUS_DELETED);
    }
}
