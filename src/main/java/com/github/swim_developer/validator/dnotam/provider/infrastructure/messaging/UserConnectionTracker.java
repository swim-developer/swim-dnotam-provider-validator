package com.github.swim_developer.validator.dnotam.provider.infrastructure.messaging;

import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConnectionTrackerPort;

import com.github.swim_developer.validator.provider.domain.model.AmqpConfig;
import com.github.swim_developer.validator.provider.domain.model.ConnectionResult;
import com.github.swim_developer.validator.provider.domain.model.UserAmqpConfig;
import com.github.swim_developer.validator.provider.domain.port.out.UserAmqpConfigRepository;
import com.github.swim_developer.validator.provider.infrastructure.messaging.UserConnectionState;
import io.vertx.amqp.AmqpClient;
import io.vertx.amqp.AmqpClientOptions;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConsoleNotificationPort;

@Slf4j
@ApplicationScoped
public class UserConnectionTracker implements ConnectionTrackerPort {

    private static final long HEARTBEAT_TIMEOUT_SECONDS = 90;
    private static final String CONSOLE_USER_PREFIX = "User ";

    private final Vertx vertx;
    private final ConsoleNotificationPort consoleService;
    private final UserAmqpConfigRepository userAmqpConfigRepository;
    private final UserReceiverLifecycle receiverLifecycle;
    private final AmqpSslConfigurator sslConfigurator;
    private final String defaultHost;
    private final int defaultPort;

    private final Map<String, UserConnectionState> userConnections = new ConcurrentHashMap<>();

    @Inject
    public UserConnectionTracker(
            Vertx vertx,
            ConsoleNotificationPort consoleService,
            UserAmqpConfigRepository userAmqpConfigRepository,
            UserReceiverLifecycle receiverLifecycle,
            AmqpSslConfigurator sslConfigurator,
            @ConfigProperty(name = "swim.provider.amqp.host") String defaultHost,
            @ConfigProperty(name = "swim.provider.amqp.port") int defaultPort) {
        this.vertx = vertx;
        this.consoleService = consoleService;
        this.userAmqpConfigRepository = userAmqpConfigRepository;
        this.receiverLifecycle = receiverLifecycle;
        this.sslConfigurator = sslConfigurator;
        this.defaultHost = defaultHost;
        this.defaultPort = defaultPort;
    }

    public UserConnectionState getOrCreateState(String userId, String username) {
        return userConnections.computeIfAbsent(userId, id -> {
            log.info("Creating new connection state for user: {} ({})", username, userId);
            return new UserConnectionState(userId, username);
        });
    }

    public Optional<UserConnectionState> getState(String userId) {
        return Optional.ofNullable(userConnections.get(userId));
    }

    @Override
    public boolean isConnected(String userId) {
        return getState(userId).map(UserConnectionState::isConnected).orElse(false);
    }

    @Override
    public void heartbeat(String userId, String token) {
        getState(userId).ifPresent(state -> {
            state.updateHeartbeat();
            if (token != null && !token.isBlank()) {
                state.setTokenWithExpiry(token);
            }
            log.debug("Heartbeat received for user: {}", state.getUsername());
        });
    }

    public void updateToken(String userId, String token) {
        getState(userId).ifPresent(state -> {
            state.setTokenWithExpiry(token);
            log.debug("Token updated for user: {}", state.getUsername());
        });
    }

    public CompletableFuture<ConnectionResult> connect(String userId, String username, AmqpConfig config) {
        CompletableFuture<ConnectionResult> future = new CompletableFuture<>();
        UserConnectionState state = getOrCreateState(userId, username);

        if (state.isConnected() && !state.isTokenExpiringSoon()) {
            log.info("User {} already connected to AMQP broker (token valid)", username);
            state.updateHeartbeat();
            state.setTokenWithExpiry(config.password());
            future.complete(ConnectionResult.ok());
            return future;
        }

        if (state.isConnected() && state.isTokenExpiringSoon()) {
            log.info("User {} connected but token expiring soon, reconnecting with fresh token", username);
            state.disconnect();
        }

        String host = config.host() != null && !config.host().isBlank() ? config.host() : defaultHost;
        int port = config.port() > 0 ? config.port() : defaultPort;
        String brokerUsername = config.username();
        String brokerPassword = config.password();

        if (brokerUsername == null || brokerUsername.isBlank()) {
            log.error("AMQP username is required");
            future.complete(ConnectionResult.fail("AMQP username is required"));
            return future;
        }
        if (brokerPassword == null || brokerPassword.isBlank()) {
            log.error("AMQP password is required");
            future.complete(ConnectionResult.fail("AMQP password is required"));
            return future;
        }

        AmqpClientOptions options = new AmqpClientOptions()
                .setHost(host).setPort(port)
                .setUsername(brokerUsername).setPassword(brokerPassword)
                .setConnectTimeout(10000).setSsl(true);

        sslConfigurator.configure(options);

        AmqpClient client = AmqpClient.create(vertx, options);
        state.setClient(client);
        state.setHost(host);
        state.setPort(port);

        log.info("User {} connecting to AMQP broker at {}:{}", username, host, port);
        consoleService.info(userId, CONSOLE_USER_PREFIX + username + " connecting to AMQP broker at " + host + ":" + port);

        String finalHost = host;
        int finalPort = port;
        String finalBrokerUsername = brokerUsername;

        client.connect()
                .onSuccess(conn -> {
                    state.setConnection(conn);
                    state.setConnectedAt(Instant.now());
                    state.updateHeartbeat();
                    state.setTokenWithExpiry(brokerPassword);
                    log.info("User {} connected to AMQP broker successfully", username);
                    consoleService.success(userId, CONSOLE_USER_PREFIX + username + " connected to AMQP broker");

                    conn.exceptionHandler(err -> {
                        log.error("AMQP connection lost for user {}: {}", username, err.getMessage());
                        consoleService.error(userId, "AMQP connection lost: " + err.getMessage());
                        state.setConnection(null);
                        state.getReceivers().clear();
                    });

                    vertx.getOrCreateContext().executeBlocking(
                            () -> persistUserAmqpConfig(userId, username, finalHost, finalPort, finalBrokerUsername), true);

                    future.complete(ConnectionResult.ok());
                })
                .onFailure(err -> {
                    log.error("User {} failed to connect to AMQP broker", username, err);
                    consoleService.error(userId, CONSOLE_USER_PREFIX + username + " failed to connect: " + err.getMessage());
                    state.disconnect();
                    future.complete(ConnectionResult.fail(err.getMessage()));
                });

        return future;
    }

    public CompletableFuture<Boolean> disconnect(String userId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        UserConnectionState state = userConnections.remove(userId);
        if (state == null) {
            future.complete(true);
            return future;
        }

        log.info("Disconnecting user: {}", state.getUsername());
        consoleService.info(userId, "Disconnecting user: " + state.getUsername());

        vertx.getOrCreateContext().executeBlocking(() -> disconnectBlocking(state), true).onComplete(ar -> {
            consoleService.success(userId, CONSOLE_USER_PREFIX + state.getUsername() + " disconnected");
            future.complete(true);
        });

        return future;
    }

    public CompletableFuture<ConnectionResult> reconnectWithFreshToken(String userId) {
        UserConnectionState state = userConnections.get(userId);
        if (state == null) {
            CompletableFuture<ConnectionResult> f = new CompletableFuture<>();
            f.complete(ConnectionResult.fail("No connection state for user"));
            return f;
        }

        String freshToken = state.getToken();
        if (freshToken == null || freshToken.isBlank()) {
            CompletableFuture<ConnectionResult> f = new CompletableFuture<>();
            f.complete(ConnectionResult.fail("No token available for reconnection"));
            return f;
        }

        log.info("Reconnecting user {} with fresh token", state.getUsername());
        consoleService.info(userId, "Reconnecting with fresh token...");
        state.disconnect();

        AmqpConfig config = new AmqpConfig(state.getHost(), state.getPort(), state.getUsername(), freshToken);
        return connect(userId, state.getUsername(), config);
    }

    @Override
    public void createReceiver(String userId, String subscriptionId, String queueName) {
        UserConnectionState state = userConnections.get(userId);
        if (state == null || !state.isConnected()) {
            log.warn("Cannot create receiver - user {} not connected", userId);
            return;
        }

        if (state.isTokenExpiringSoon()) {
            reconnectWithFreshToken(userId).thenAccept(result -> {
                if (result.succeeded()) {
                    receiverLifecycle.createReceiver(userId, subscriptionId, queueName, userConnections.get(userId));
                } else {
                    log.error("Reconnection failed for user {}: {}", state.getUsername(), result.errorMessage());
                }
            });
            return;
        }

        receiverLifecycle.createReceiver(userId, subscriptionId, queueName, state);
    }

    @Override
    public void createReceiversForUser(String userId) {
        receiverLifecycle.createReceiversForUser(userId, userConnections.get(userId));
    }

    public CompletableFuture<String> testQueueAccess(String userId, String queueName, String token) {
        CompletableFuture<String> future = new CompletableFuture<>();
        UserConnectionState state = userConnections.get(userId);
        if (state == null || !state.isConnected()) {
            future.complete("Not connected to AMQP broker");
            return future;
        }

        if (token != null && !token.isBlank()) {
            state.setTokenWithExpiry(token);
        }

        if (state.isTokenExpiringSoon()) {
            reconnectWithFreshToken(userId).thenAccept(result -> {
                if (result.succeeded()) {
                    receiverLifecycle.testQueueAccess(userId, queueName, userConnections.get(userId), future);
                } else {
                    future.complete("Reconnection failed: " + result.errorMessage());
                }
            });
            return future;
        }

        receiverLifecycle.testQueueAccess(userId, queueName, state, future);
        return future;
    }

    public void createReceiverForQueue(String userId, String subscriptionId, String queueName) {
        createReceiver(userId, subscriptionId, queueName);
    }

    @Override
    public void closeReceiverForQueue(String userId, String queueName) {
        receiverLifecycle.closeReceiver(userId, queueName, userConnections.get(userId));
    }

    public Map<String, Boolean> getReceiverStatus(String userId) {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        getState(userId).ifPresent(state ->
                state.getReceivers().keySet().forEach(q -> status.put(q, true)));
        return status;
    }

    public String getHost(String userId) {
        return getState(userId).map(UserConnectionState::getHost).orElse(defaultHost);
    }

    public int getPort(String userId) {
        return getState(userId).map(UserConnectionState::getPort).orElse(defaultPort);
    }

    @Override
    public int getActiveReceiverCount(String userId) {
        return getState(userId).map(UserConnectionState::getActiveReceiverCount).orElse(0);
    }

    public String getDefaultHost() {
        return defaultHost;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public int getActiveConnectionCount() {
        return (int) userConnections.values().stream().filter(UserConnectionState::isConnected).count();
    }

    @Override
    public void performCleanup() {
        List<String> expiredUsers = new ArrayList<>();

        for (Map.Entry<String, UserConnectionState> entry : userConnections.entrySet()) {
            UserConnectionState state = entry.getValue();
            if (state.isExpired(HEARTBEAT_TIMEOUT_SECONDS)) {
                expiredUsers.add(entry.getKey());
                log.warn("Connection expired for user: {} (last heartbeat: {})",
                        state.getUsername(), state.getLastHeartbeat());
            }
        }

        for (String userId : expiredUsers) {
            log.info("Cleaning up expired connection for user: {}", userId);
            disconnect(userId);
        }

        if (!expiredUsers.isEmpty()) {
            log.info("Cleaned up {} expired connections", expiredUsers.size());
        }

        reconcileReceivers();
    }

    void reconcileReceivers() {
        for (Map.Entry<String, UserConnectionState> entry : userConnections.entrySet()) {
            UserConnectionState state = entry.getValue();
            if (state.isConnected()) {
                if (state.isTokenExpiringSoon() && state.getToken() != null) {
                    log.info("Token expiring for user {} during reconciliation, reconnecting", state.getUsername());
                    reconnectWithFreshToken(entry.getKey()).thenAccept(result -> {
                        if (result.succeeded() && state.getActiveReceiverCount() == 0) {
                            createReceiversForUser(entry.getKey());
                        }
                    });
                } else if (state.getActiveReceiverCount() == 0) {
                    log.info("Reconciling receivers for user: {} (connected but 0 receivers)", state.getUsername());
                    createReceiversForUser(entry.getKey());
                }
            }
        }
    }

    @Transactional
    void saveUserAmqpConfig(String userId, String username, String host, int port, String brokerUsername) {
        try {
            UserAmqpConfig existing = userAmqpConfigRepository.findByUserId(userId).orElse(null);
            if (existing == null) {
                UserAmqpConfig config = new UserAmqpConfig();
                config.setUserId(userId);
                config.setUsername(username);
                config.setAmqpHost(host);
                config.setAmqpPort(port);
                config.setBrokerUsername(brokerUsername);
                config.setUpdatedAt(java.time.LocalDateTime.now());
                userAmqpConfigRepository.insert(config);
            } else {
                existing.setAmqpHost(host);
                existing.setAmqpPort(port);
                existing.setBrokerUsername(brokerUsername);
                existing.setUpdatedAt(java.time.LocalDateTime.now());
                userAmqpConfigRepository.update(existing);
            }
            log.debug("Saved AMQP config for user: {}", username);
        } catch (Exception e) {
            log.warn("Failed to save AMQP config for user {}: {}", username, e.getMessage());
        }
    }

    @Transactional
    public Optional<UserAmqpConfig> getLastUsedConfig(String userId) {
        return userAmqpConfigRepository.findByUserId(userId);
    }

    private Void persistUserAmqpConfig(String userId, String username, String host, int port, String brokerUsername) {
        saveUserAmqpConfig(userId, username, host, port, brokerUsername);
        return null;
    }

    private Void disconnectBlocking(UserConnectionState state) {
        state.disconnect();
        return null;
    }
}
