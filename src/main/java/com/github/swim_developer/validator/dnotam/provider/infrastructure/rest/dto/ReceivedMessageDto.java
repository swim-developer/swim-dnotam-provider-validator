package com.github.swim_developer.validator.dnotam.provider.infrastructure.rest.dto;

import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record ReceivedMessageDto(
    Long id,
    String subscriptionId,
    String queueName,
    String messageId,
    String contentType,
    String eventId,
    String eventScenario,
    String airportHeliport,
    String airspace,
    String eventSeries,
    String publisher,
    String provider,
    String validFrom,
    String validTo,
    String receivedAt,
    Double latitude,
    Double longitude,
    String notamCoordinates,
    String bodyPreview
) {
    private static final int BODY_PREVIEW_LENGTH = 300;

    public static ReceivedMessageDto from(ReceivedMessage entity) {
        String preview = null;
        if (entity.getBody() != null) {
            String trimmed = entity.getBody().strip();
            preview = trimmed.length() > BODY_PREVIEW_LENGTH
                    ? trimmed.substring(0, BODY_PREVIEW_LENGTH) + "…"
                    : trimmed;
        }
        return new ReceivedMessageDto(
            entity.getId(),
            entity.getSubscriptionId(),
            entity.getQueueName(),
            entity.getMessageId(),
            entity.getContentType(),
            entity.getEventId(),
            entity.getEventScenario(),
            entity.getAirportHeliport(),
            entity.getAirspace(),
            entity.getEventSeries(),
            entity.getPublisher(),
            entity.getProvider(),
            entity.getValidFrom() != null ? entity.getValidFrom().toString() : null,
            entity.getValidTo() != null ? entity.getValidTo().toString() : null,
            entity.getReceivedAt() != null ? entity.getReceivedAt().toString() : null,
            entity.getLatitude(),
            entity.getLongitude(),
            entity.getNotamCoordinates(),
            preview
        );
    }
}

