package com.github.swim_developer.validator.dnotam.provider.application.usecase;

import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConsoleNotificationPort;

import com.github.swim_developer.validator.provider.domain.model.ConsoleEntry;
import com.github.swim_developer.validator.dnotam.provider.domain.model.MessageData;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import com.github.swim_developer.validator.dnotam.provider.application.port.in.ConsoleStreamPort;

@ApplicationScoped
public class ConsoleService implements ConsoleNotificationPort , ConsoleStreamPort {

    private final BroadcastProcessor<ConsoleEntry> processor = BroadcastProcessor.create();

    @Getter
    private final Multi<ConsoleEntry> stream = processor;

    @Override
    public void info(String userId, String message) {
        emit(userId, "info", message);
    }

    @Override
    public void success(String userId, String message) {
        emit(userId, "success", message);
    }

    @Override
    public void warning(String userId, String message) {
        emit(userId, "warning", message);
    }

    @Override
    public void error(String userId, String message) {
        emit(userId, "error", message);
    }

    public void amqpConnected(String userId) {
        emit(userId, "amqp_connected", "AMQP broker connected");
    }

    public void amqpDisconnected(String userId) {
        emit(userId, "amqp_disconnected", "AMQP broker disconnected");
    }

    public void amqpError(String userId, String errorMessage) {
        emit(userId, "amqp_error", errorMessage);
    }

    @Override
    public void messageReceived(String userId, MessageData messageData) {
        var entry = new ConsoleEntry(
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            "message_received",
            toJson(messageData),
            userId
        );
        processor.onNext(entry);
    }

    public Multi<ConsoleEntry> streamForUser(String userId) {
        return stream.filter(entry -> entry.userId() == null || entry.userId().equals(userId));
    }

    private void emit(String userId, String type, String message) {
        var entry = new ConsoleEntry(
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            type,
            message,
            userId
        );
        processor.onNext(entry);
    }

    private String toJson(MessageData data) {
        return String.format(
            "{\"id\":%d,\"subscriptionId\":\"%s\",\"queueName\":\"%s\",\"eventId\":\"%s\"," +
            "\"eventScenario\":\"%s\",\"airportHeliport\":\"%s\",\"airspace\":\"%s\"," +
            "\"eventSeries\":\"%s\",\"publisher\":\"%s\",\"provider\":\"%s\"," +
            "\"validFrom\":\"%s\",\"validTo\":\"%s\",\"receivedAt\":\"%s\"}",
            data.id(),
            nullSafe(data.subscriptionId()),
            nullSafe(data.queueName()),
            nullSafe(data.eventId()),
            nullSafe(data.eventScenario()),
            nullSafe(data.airportHeliport()),
            nullSafe(data.airspace()),
            nullSafe(data.eventSeries()),
            nullSafe(data.publisher()),
            nullSafe(data.provider()),
            nullSafe(data.validFrom()),
            nullSafe(data.validTo()),
            nullSafe(data.receivedAt())
        );
    }

    private String nullSafe(Object value) {
        return value != null ? value.toString() : "";
    }
}

