package com.github.swim_developer.validator.dnotam.provider.domain.port.out;

import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;

import java.time.LocalDateTime;
import java.util.List;

public interface ReceivedMessageRepository {
    ReceivedMessage insert(ReceivedMessage entity);
    ReceivedMessage findMessageById(Long id);
    List<ReceivedMessage> findBySubscriptionId(String subscriptionId);
    List<ReceivedMessage> findByQueueName(String queueName);
    long countBySubscriptionId(String subscriptionId);
    List<ReceivedMessage> findBySubscriptionIds(List<String> ids);
    List<ReceivedMessage> findBySubscriptionIdsAfter(List<String> ids, LocalDateTime after);
    List<ReceivedMessage> findBySubscriptionIdsFiltered(List<String> ids, boolean xmlOnly);
    List<ReceivedMessage> findBySubscriptionIdsAfterFiltered(List<String> ids, LocalDateTime after, boolean xmlOnly);
    List<ReceivedMessage> findRecentMessages();
    List<ReceivedMessage> findMessagesAfter(LocalDateTime after);
}
