package com.github.swim_developer.validator.dnotam.provider.domain.model;



public record MessageData(
    Long id,
    String subscriptionId,
    String queueName,
    String eventId,
    String eventScenario,
    String airportHeliport,
    String airspace,
    String eventSeries,
    String publisher,
    String provider,
    String validFrom,
    String validTo,
    String receivedAt
) {}
