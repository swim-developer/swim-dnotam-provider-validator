package com.github.swim_developer.validator.dnotam.provider.domain.port.in;

import com.github.swim_developer.validator.provider.domain.model.Subscription;

import java.util.List;

public interface ProviderSubscriptionPort {
    void createSubscription(String responseBody, String userId, String username,
            String providerUrl, String requestBody);
    void updateSubscription(String responseBody, String userId, String subscriptionId);
    void deleteSubscription(String userId, String subscriptionId);
    List<Subscription> getSubscriptionsByUsername(String username);
    List<Subscription> getActiveSubscriptionsByUsername(String username);
    long countActiveSubscriptionsByUsername(String username);
}
