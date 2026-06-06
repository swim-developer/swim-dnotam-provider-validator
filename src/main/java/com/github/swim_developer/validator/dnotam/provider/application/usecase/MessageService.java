package com.github.swim_developer.validator.dnotam.provider.application.usecase;

import com.github.swim_developer.validator.dnotam.provider.domain.port.in.MessagePersistencePort;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConsoleNotificationPort;
import com.github.swim_developer.validator.dnotam.provider.domain.model.DnotamEvent;
import com.github.swim_developer.validator.dnotam.provider.domain.model.MessageData;
import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;
import com.github.swim_developer.validator.dnotam.provider.domain.port.out.ReceivedMessageRepository;
import com.github.swim_developer.validator.provider.domain.model.Subscription;
import com.github.swim_developer.validator.provider.domain.port.out.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.MessagePort;

@Slf4j
@ApplicationScoped
public class MessageService implements MessagePersistencePort , MessagePort {

    private final ConsoleNotificationPort consoleService;
    private final DnotamEventExtractor eventExtractor;
    private final ReceivedMessageRepository messageRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Inject
    public MessageService(
            ConsoleNotificationPort consoleService,
            DnotamEventExtractor eventExtractor,
            ReceivedMessageRepository messageRepository,
            SubscriptionRepository subscriptionRepository) {
        this.consoleService = consoleService;
        this.eventExtractor = eventExtractor;
        this.messageRepository = messageRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    @Override
    public ReceivedMessage saveMessage(String userId, String subscriptionId, String queueName, String messageId, 
                                        String contentType, String body) {
        ReceivedMessage message = new ReceivedMessage();
        message.setSubscriptionId(subscriptionId);
        message.setQueueName(queueName);
        message.setMessageId(messageId);
        message.setContentType(contentType);
        message.setBody(body);
        message.setReceivedAt(LocalDateTime.now());

        if (contentType != null && contentType.toLowerCase().contains("xml")) {
            var extractedEvent = eventExtractor.extractFromAixmMessage(body);
            extractedEvent.ifPresentOrElse(
                event -> applyEventFields(message, event),
                () -> log.warn("Could not extract DNOTAM event from message {}", messageId)
            );
        } else {
            log.debug("Skipping AIXM extraction for non-XML message: contentType={}, messageId={}", contentType, messageId);
        }

        ReceivedMessage savedMessage = messageRepository.insert(message);
        log.info("Message saved: {} from queue {}", messageId, queueName);
        MessageData messageData = new MessageData(
            savedMessage.getId(),
            savedMessage.getSubscriptionId(),
            savedMessage.getQueueName(),
            savedMessage.getEventId(),
            savedMessage.getEventScenario(),
            savedMessage.getAirportHeliport(),
            savedMessage.getAirspace(),
            savedMessage.getEventSeries(),
            savedMessage.getPublisher(),
            savedMessage.getProvider(),
            savedMessage.getValidFrom() != null ? savedMessage.getValidFrom().toString() : null,
            savedMessage.getValidTo() != null ? savedMessage.getValidTo().toString() : null,
            savedMessage.getReceivedAt() != null ? savedMessage.getReceivedAt().toString() : null
        );
        consoleService.messageReceived(userId, messageData);

        return savedMessage;
    }

    public List<ReceivedMessage> getMessagesBySubscription(String subscriptionId) {
        return messageRepository.findBySubscriptionId(subscriptionId);
    }

    public List<ReceivedMessage> getMessagesByQueue(String queueName) {
        return messageRepository.findByQueueName(queueName);
    }

    public long getMessageCount(String subscriptionId) {
        return messageRepository.countBySubscriptionId(subscriptionId);
    }

    public long getMessageCountByUsername(String subscriptionId, String username) {
        return messageRepository.countBySubscriptionId(subscriptionId);
    }

    public List<ReceivedMessage> getRecentMessages(int limit) {
        List<ReceivedMessage> all = messageRepository.findRecentMessages();
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    public List<ReceivedMessage> getMessagesByUsername(String username) {
        List<Subscription> userSubscriptions = subscriptionRepository.findByUsername(username);
        if (userSubscriptions.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> subscriptionIds = userSubscriptions.stream()
            .map(Subscription::getSubscriptionId)
            .toList();

        return messageRepository.findBySubscriptionIds(subscriptionIds);
    }

    public List<ReceivedMessage> getMessagesByUsernameAfter(String username, java.time.LocalDateTime after) {
        List<Subscription> userSubscriptions = subscriptionRepository.findByUsername(username);
        if (userSubscriptions.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> subscriptionIds = userSubscriptions.stream()
            .map(Subscription::getSubscriptionId)
            .toList();

        return messageRepository.findBySubscriptionIdsAfter(subscriptionIds, after);
    }

    public List<ReceivedMessage> getMessagesByUsernameAndType(String username, String type) {
        List<Subscription> userSubscriptions = subscriptionRepository.findByUsername(username);
        if (userSubscriptions.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> ids = userSubscriptions.stream().map(Subscription::getSubscriptionId).toList();
        boolean xmlOnly = "business".equalsIgnoreCase(type);
        return messageRepository.findBySubscriptionIdsFiltered(ids, xmlOnly);
    }

    public List<ReceivedMessage> getMessagesByUsernameAfterAndType(String username, java.time.LocalDateTime after, String type) {
        List<Subscription> userSubscriptions = subscriptionRepository.findByUsername(username);
        if (userSubscriptions.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> ids = userSubscriptions.stream().map(Subscription::getSubscriptionId).toList();
        boolean xmlOnly = "business".equalsIgnoreCase(type);
        return messageRepository.findBySubscriptionIdsAfterFiltered(ids, after, xmlOnly);
    }

    public List<ReceivedMessage> getAllMessages() {
        return messageRepository.findRecentMessages();
    }

    public List<ReceivedMessage> getAllMessagesAfter(java.time.LocalDateTime after) {
        return messageRepository.findMessagesAfter(after);
    }

    private void applyEventFields(ReceivedMessage message, DnotamEvent event) {
        message.setEventId(event.eventId());
        message.setEventScenario(event.eventScenario());
        message.setAirportHeliport(event.airportHeliport());
        message.setAirspace(event.airspace());
        message.setEventSeries(event.eventSeries());
        message.setPublisher(event.publisher());
        message.setProvider(event.provider());
        message.setValidFrom(event.validFrom());
        message.setValidTo(event.validTo());
        message.setLatitude(event.latitude());
        message.setLongitude(event.longitude());
        message.setNotamCoordinates(event.notamCoordinates());
        log.info("Extracted DNOTAM event: {} scenario={} airport={} coords=[{}, {}]",
            event.eventId(), event.eventScenario(), event.airportHeliport(),
            event.latitude(), event.longitude());
    }
}
