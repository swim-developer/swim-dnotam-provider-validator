package com.github.swim_developer.validator.dnotam.provider.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "received_messages")
@Getter
@Setter
@NoArgsConstructor
public class ReceivedMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id")
    private String subscriptionId;

    @Column(name = "queue_name")
    private String queueName;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_scenario")
    private String eventScenario;

    @Column(name = "airport_heliport")
    private String airportHeliport;

    @Column(name = "airspace")
    private String airspace;

    @Column(name = "event_series")
    private String eventSeries;

    @Column(name = "publisher")
    private String publisher;

    @Column(name = "provider")
    private String provider;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "notam_coordinates")
    private String notamCoordinates;

    @Lob
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;
}
