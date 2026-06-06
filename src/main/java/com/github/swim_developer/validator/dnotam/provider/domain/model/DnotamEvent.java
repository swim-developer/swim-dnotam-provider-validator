package com.github.swim_developer.validator.dnotam.provider.domain.model;

import java.time.Instant;


public record DnotamEvent(
        String eventId,
        String eventScenario,
        String airportHeliport,
        String airspace,
        String eventSeries,
        String publisher,
        String provider,
        Instant validFrom,
        Instant validTo,
        String aixmMessage,
        Double latitude,
        Double longitude,
        String notamCoordinates
) {
}
