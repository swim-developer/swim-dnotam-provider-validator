package com.github.swim_developer.validator.dnotam.provider.domain.port.in;

import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;

import java.time.LocalDateTime;
import java.util.List;

public interface MessagePort {
    ReceivedMessage saveMessage(String userId, String subscriptionId, String queueName,
            String messageId, String contentType, String body);
    List<ReceivedMessage> getMessagesBySubscription(String subscriptionId);
    List<ReceivedMessage> getMessagesByQueue(String queueName);
    long getMessageCount(String subscriptionId);
    long getMessageCountByUsername(String subscriptionId, String username);
    List<ReceivedMessage> getRecentMessages(int limit);
    List<ReceivedMessage> getMessagesByUsername(String username);
    List<ReceivedMessage> getMessagesByUsernameAfter(String username, LocalDateTime after);
    List<ReceivedMessage> getMessagesByUsernameAndType(String username, String type);
    List<ReceivedMessage> getMessagesByUsernameAfterAndType(String username, LocalDateTime after, String type);
    List<ReceivedMessage> getAllMessages();
    List<ReceivedMessage> getAllMessagesAfter(LocalDateTime after);
}
