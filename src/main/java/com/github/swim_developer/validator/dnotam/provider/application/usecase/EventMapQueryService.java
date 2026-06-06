package com.github.swim_developer.validator.dnotam.provider.application.usecase;

import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;
import com.github.swim_developer.validator.dnotam.provider.domain.port.out.MapRenderPort;
import com.github.swim_developer.validator.dnotam.provider.domain.port.out.ReceivedMessageRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.MapQueryPort;

@Slf4j
@ApplicationScoped
public class EventMapQueryService implements MapQueryPort {

    private final ReceivedMessageRepository messageRepository;
    private final MapRenderPort mapRenderer;

    @Inject
    public EventMapQueryService(ReceivedMessageRepository messageRepository, MapRenderPort mapRenderer) {
        this.messageRepository = messageRepository;
        this.mapRenderer = mapRenderer;
    }

    public String generateMapWithAllEvents() {
        List<ReceivedMessage> allEvents = messageRepository.findRecentMessages();
        return mapRenderer.generateMapWithEvents(allEvents);
    }

    public String generateMapWithRecentEvents(int limit) {
        List<ReceivedMessage> events = messageRepository.findRecentMessages();
        List<ReceivedMessage> limited = events.size() > limit ? events.subList(0, limit) : events;
        return mapRenderer.generateMapWithEvents(limited);
    }

    public String generateMapWithRecentMinutes(int minutes) {
        List<ReceivedMessage> events = messageRepository.findMessagesAfter(
                LocalDateTime.now().minusMinutes(minutes)
        );
        return mapRenderer.generateMapWithEvents(events);
    }
}
