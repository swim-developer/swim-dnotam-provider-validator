package com.github.swim_developer.validator.dnotam.provider.infrastructure.persistence;

import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;
import com.github.swim_developer.validator.dnotam.provider.infrastructure.persistence.entity.ReceivedMessageEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReceivedMessageMapper {

    public ReceivedMessageEntity toEntity(ReceivedMessage domain) {
        ReceivedMessageEntity entity = new ReceivedMessageEntity();
        entity.setId(domain.getId());
        entity.setSubscriptionId(domain.getSubscriptionId());
        entity.setQueueName(domain.getQueueName());
        entity.setMessageId(domain.getMessageId());
        entity.setContentType(domain.getContentType());
        entity.setEventId(domain.getEventId());
        entity.setEventScenario(domain.getEventScenario());
        entity.setAirportHeliport(domain.getAirportHeliport());
        entity.setAirspace(domain.getAirspace());
        entity.setEventSeries(domain.getEventSeries());
        entity.setPublisher(domain.getPublisher());
        entity.setProvider(domain.getProvider());
        entity.setValidFrom(domain.getValidFrom());
        entity.setValidTo(domain.getValidTo());
        entity.setLatitude(domain.getLatitude());
        entity.setLongitude(domain.getLongitude());
        entity.setNotamCoordinates(domain.getNotamCoordinates());
        entity.setBody(domain.getBody());
        entity.setReceivedAt(domain.getReceivedAt());
        return entity;
    }

    public ReceivedMessage toDomain(ReceivedMessageEntity entity) {
        ReceivedMessage domain = new ReceivedMessage();
        domain.setId(entity.getId());
        domain.setSubscriptionId(entity.getSubscriptionId());
        domain.setQueueName(entity.getQueueName());
        domain.setMessageId(entity.getMessageId());
        domain.setContentType(entity.getContentType());
        domain.setEventId(entity.getEventId());
        domain.setEventScenario(entity.getEventScenario());
        domain.setAirportHeliport(entity.getAirportHeliport());
        domain.setAirspace(entity.getAirspace());
        domain.setEventSeries(entity.getEventSeries());
        domain.setPublisher(entity.getPublisher());
        domain.setProvider(entity.getProvider());
        domain.setValidFrom(entity.getValidFrom());
        domain.setValidTo(entity.getValidTo());
        domain.setLatitude(entity.getLatitude());
        domain.setLongitude(entity.getLongitude());
        domain.setNotamCoordinates(entity.getNotamCoordinates());
        domain.setBody(entity.getBody());
        domain.setReceivedAt(entity.getReceivedAt());
        return domain;
    }
}
