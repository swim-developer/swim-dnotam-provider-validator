package com.github.swim_developer.validator.dnotam.provider.domain.port.in;

import com.github.swim_developer.validator.provider.domain.model.UserAmqpConfig;
import com.github.swim_developer.validator.provider.domain.model.AmqpConfig;
import com.github.swim_developer.validator.provider.domain.model.ConnectionResult;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ConnectionTrackerPort {
    boolean isConnected(String userId);
    void createReceiver(String userId, String subscriptionId, String queueName);
    void closeReceiverForQueue(String userId, String queueName);
    void heartbeat(String userId, String token);
    void createReceiversForUser(String userId);
    void performCleanup();
    int getActiveReceiverCount(String userId);
    CompletableFuture<ConnectionResult> connect(String userId, String username, AmqpConfig config);
    CompletableFuture<Boolean> disconnect(String userId);
    CompletableFuture<String> testQueueAccess(String userId, String queueName, String token);
    Map<String, Boolean> getReceiverStatus(String userId);
    String getHost(String userId);
    int getPort(String userId);
    String getDefaultHost();
    int getDefaultPort();
    Optional<UserAmqpConfig> getLastUsedConfig(String userId);
}
