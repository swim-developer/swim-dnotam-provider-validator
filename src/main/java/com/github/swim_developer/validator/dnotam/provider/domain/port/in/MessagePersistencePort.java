package com.github.swim_developer.validator.dnotam.provider.domain.port.in;

import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;

public interface MessagePersistencePort {
    ReceivedMessage saveMessage(String userId, String subscriptionId, String queueName,
            String messageId, String contentType, String body);
}
