package com.github.swim_developer.validator.dnotam.provider.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ReceivedMessage {

    private Long id;
    private String subscriptionId;
    private String queueName;
    private String messageId;
    private String contentType;
    private String eventId;
    private String eventScenario;
    private String airportHeliport;
    private String airspace;
    private String eventSeries;
    private String publisher;
    private String provider;
    private Instant validFrom;
    private Instant validTo;
    private Double latitude;
    private Double longitude;
    private String notamCoordinates;
    private String body;
    private LocalDateTime receivedAt;
}
