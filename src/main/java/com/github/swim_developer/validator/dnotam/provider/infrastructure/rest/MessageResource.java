package com.github.swim_developer.validator.dnotam.provider.infrastructure.rest;

import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConsoleNotificationPort;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.MessagePort;
import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;
import com.github.swim_developer.validator.dnotam.provider.domain.port.out.ReceivedMessageRepository;
import com.github.swim_developer.validator.dnotam.provider.infrastructure.rest.dto.ReceivedMessageDto;
import com.github.swim_developer.validator.provider.infrastructure.rest.dto.UserInfo;
import com.github.swim_developer.validator.provider.infrastructure.security.JwtService;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConnectionTrackerPort;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Path("/api")
public class MessageResource {

    private static final String PLACEHOLDER_UNKNOWN = "unknown";

    private final MessagePort messageService;
    private final ReceivedMessageRepository messageRepository;
    private final JwtService jwtService;
    private final ConnectionTrackerPort connectionManager;
    private final ConsoleNotificationPort consoleService;
    private final ApiHtmlHelper htmlHelper;

    @Inject
    public MessageResource(
            MessagePort messageService,
            ReceivedMessageRepository messageRepository,
            JwtService jwtService,
            ConnectionTrackerPort connectionManager,
            ConsoleNotificationPort consoleService,
            ApiHtmlHelper htmlHelper) {
        this.messageService = messageService;
        this.messageRepository = messageRepository;
        this.jwtService = jwtService;
        this.connectionManager = connectionManager;
        this.consoleService = consoleService;
        this.htmlHelper = htmlHelper;
    }

    @GET
    @Path("/messages/{id}/xml")
    @Produces(MediaType.TEXT_HTML)
    public Response viewMessageXml(@PathParam("id") Long id) {
        ReceivedMessage message = messageRepository.findMessageById(id);
        if (message == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(htmlHelper.formatXmlForDisplay(message.getBody(), message.getEventId())).build();
    }

    @GET
    @Path("/messages/{id}/download")
    @Produces("application/xml")
    public Response downloadMessageXml(@PathParam("id") Long id) {
        ReceivedMessage message = messageRepository.findMessageById(id);
        if (message == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String filename = (message.getEventId() != null ? message.getEventId() : "message-" + id) + ".xml";
        return Response.ok(message.getBody())
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
    }

    @GET
    @Path("/user/messages")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserMessages(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("minutes") Integer minutes,
            @QueryParam("type") String type) {
        Optional<UserInfo> userOpt = jwtService.extractUserFromHeader(authHeader);
        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        UserInfo user = userOpt.get();

        String token = authHeader != null ? authHeader.replace("Bearer ", "") : null;
        connectionManager.heartbeat(user.userId(), token);

        boolean connected = connectionManager.isConnected(user.userId());
        int activeReceivers = connectionManager.getActiveReceiverCount(user.userId());

        if (connected && activeReceivers == 0) {
            connectionManager.createReceiversForUser(user.userId());
        }

        List<ReceivedMessage> userMessages;
        boolean hasType = type != null && !type.isBlank();
        boolean hasWindow = minutes != null && minutes > 0;

        if (hasType && hasWindow) {
            userMessages = messageService.getMessagesByUsernameAfterAndType(
                    user.username(), LocalDateTime.now().minusMinutes(minutes), type);
        } else if (hasType) {
            userMessages = messageService.getMessagesByUsernameAndType(user.username(), type);
        } else if (hasWindow) {
            userMessages = messageService.getMessagesByUsernameAfter(
                    user.username(), LocalDateTime.now().minusMinutes(minutes));
        } else {
            userMessages = messageService.getMessagesByUsername(user.username());
        }

        List<ReceivedMessageDto> messagesDto = userMessages.stream().map(ReceivedMessageDto::from).toList();

        return Response.ok(Map.of(
                "messages", messagesDto,
                "messageCount", userMessages.size(),
                "activeReceivers", activeReceivers,
                "connected", connected
        )).build();
    }

    @POST
    @Path("/events/inject")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML, "application/aixm+xml"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response injectDnotamEvent(String aixmXml) {
        if (aixmXml == null || aixmXml.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Empty or null AIXM XML body"))
                    .build();
        }

        String subscriptionId = "injected-" + UUID.randomUUID().toString().substring(0, 8);
        String queueName = "injected.events";
        String messageId = "msg-" + UUID.randomUUID().toString();
        String contentType = "application/aixm+xml";

        try {
            ReceivedMessage saved = messageService.saveMessage(null, subscriptionId, queueName, messageId, contentType, aixmXml);
            consoleService.success(null, "Event injected via REST: " + saved.getEventId());
            return Response.status(Response.Status.CREATED)
                    .entity(Map.of(
                            "id", saved.getId(),
                            "messageId", messageId,
                            "eventId", saved.getEventId() != null ? saved.getEventId() : PLACEHOLDER_UNKNOWN,
                            "eventScenario", saved.getEventScenario() != null ? saved.getEventScenario() : PLACEHOLDER_UNKNOWN,
                            "airportHeliport", saved.getAirportHeliport() != null ? saved.getAirportHeliport() : PLACEHOLDER_UNKNOWN,
                            "source", "REST injection"
                    )).build();
        } catch (Exception e) {
            consoleService.error(null, "Failed to inject event: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
