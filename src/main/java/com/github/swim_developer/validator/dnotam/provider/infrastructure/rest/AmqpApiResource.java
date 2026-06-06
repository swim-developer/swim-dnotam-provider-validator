package com.github.swim_developer.validator.dnotam.provider.infrastructure.rest;

import com.github.swim_developer.validator.dnotam.provider.infrastructure.client.ProviderHttpClient;

import com.github.swim_developer.validator.dnotam.provider.infrastructure.rest.dto.ReceivedMessageDto;
import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.MessagePort;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ProviderSubscriptionPort;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConnectionTrackerPort;
import com.github.swim_developer.validator.provider.domain.model.AmqpConfig;
import com.github.swim_developer.validator.provider.infrastructure.rest.dto.AmqpStatusResponse;
import com.github.swim_developer.validator.provider.infrastructure.rest.dto.QueueTestRequest;
import com.github.swim_developer.validator.provider.infrastructure.rest.dto.SubscriptionSummary;
import com.github.swim_developer.validator.provider.infrastructure.rest.dto.UserInfo;
import com.github.swim_developer.validator.provider.domain.model.Subscription;
import com.github.swim_developer.validator.provider.domain.model.UserAmqpConfig;
import com.github.swim_developer.validator.provider.domain.port.out.SubscriptionRepository;
import com.github.swim_developer.validator.provider.infrastructure.security.JwtService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Slf4j
@Path("/api/amqp")
public class AmqpApiResource {

    private static final String JSON_KEY_ERROR = "error";
    private static final String JSON_KEY_SUCCESS = "success";
    private static final String JSON_KEY_CONNECTED = "connected";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MSG_AUTHENTICATION_REQUIRED = "Authentication required";

    private final ConnectionTrackerPort connectionManager;
    private final ProviderSubscriptionPort subscriptionService;
    private final MessagePort messageService;
    private final JwtService jwtService;
    private final ProviderHttpClient providerHttpClient;
    private final SubscriptionRepository subscriptionRepository;

    @Inject
    public AmqpApiResource(
            ConnectionTrackerPort connectionManager,
            ProviderSubscriptionPort subscriptionService,
            MessagePort messageService,
            JwtService jwtService,
            ProviderHttpClient providerHttpClient,
            SubscriptionRepository subscriptionRepository) {
        this.connectionManager = connectionManager;
        this.subscriptionService = subscriptionService;
        this.messageService = messageService;
        this.jwtService = jwtService;
        this.providerHttpClient = providerHttpClient;
        this.subscriptionRepository = subscriptionRepository;
    }

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        Optional<UserInfo> userOpt = jwtService.extractUserInfo(authHeader);

        if (userOpt.isEmpty()) {
            return Response.ok(AmqpStatusResponse.unauthenticated(
                connectionManager.getDefaultHost(),
                connectionManager.getDefaultPort()
            )).build();
        }

        UserInfo user = userOpt.get();
        boolean connected = connectionManager.isConnected(user.userId());
        Optional<UserAmqpConfig> lastConfig = connectionManager.getLastUsedConfig(user.userId());

        String host = connected ? connectionManager.getHost(user.userId()) 
            : lastConfig.map(UserAmqpConfig::getAmqpHost).orElse(connectionManager.getDefaultHost());
        int port = connected ? connectionManager.getPort(user.userId())
            : lastConfig.map(UserAmqpConfig::getAmqpPort).orElse(connectionManager.getDefaultPort());
        String brokerUsername = lastConfig.map(UserAmqpConfig::getBrokerUsername).orElse("");
        long activeSubscriptions = subscriptionService.countActiveSubscriptionsByUsername(user.username());

        return Response.ok(new AmqpStatusResponse(
            true,
            user.userId(),
            user.username(),
            connected,
            host,
            port,
            brokerUsername,
            connectionManager.getActiveReceiverCount(user.userId()),
            activeSubscriptions,
            connectionManager.getReceiverStatus(user.userId())
        )).build();
    }

    @POST
    @Path("/heartbeat")
    @Produces(MediaType.APPLICATION_JSON)
    public Response heartbeat(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        Optional<UserInfo> userOpt = jwtService.extractUserInfo(authHeader);

        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of(JSON_KEY_ERROR, "Invalid or expired token"))
                .build();
        }

        UserInfo user = userOpt.get();
        String token = authHeader.replace(BEARER_PREFIX, "");
        connectionManager.heartbeat(user.userId(), token);

        return Response.ok(Map.of(
            JSON_KEY_SUCCESS, true,
            "userId", user.userId(),
            JSON_KEY_CONNECTED, connectionManager.isConnected(user.userId())
        )).build();
    }

    @POST
    @Path("/connect")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response connect(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
            AmqpConfig config) {

        Optional<UserInfo> userOpt = jwtService.extractUserInfo(authHeader);

        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of(JSON_KEY_SUCCESS, false, JSON_KEY_ERROR, MSG_AUTHENTICATION_REQUIRED))
                .build();
        }

        UserInfo user = userOpt.get();
        String token = authHeader.replace(BEARER_PREFIX, "");
        AmqpConfig brokerConfig = new AmqpConfig(config.host(), config.port(), user.username(), token);

        try {
            var result = connectionManager.connect(user.userId(), user.username(), brokerConfig).get();
            if (result.succeeded()) {
                connectionManager.createReceiversForUser(user.userId());
                return Response.ok(Map.of(JSON_KEY_SUCCESS, true, JSON_KEY_CONNECTED, true)).build();
            } else {
                return Response.ok(Map.of(JSON_KEY_SUCCESS, false, JSON_KEY_CONNECTED, false, JSON_KEY_ERROR, result.errorMessage())).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.ok(Map.of(JSON_KEY_SUCCESS, false, JSON_KEY_CONNECTED, false, JSON_KEY_ERROR, e.getMessage())).build();
        } catch (ExecutionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return Response.ok(Map.of(JSON_KEY_SUCCESS, false, JSON_KEY_CONNECTED, false, JSON_KEY_ERROR, c.getMessage())).build();
        }
    }

    @POST
    @Path("/disconnect")
    @Produces(MediaType.APPLICATION_JSON)
    public Response disconnect(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        Optional<UserInfo> userOpt = jwtService.extractUserInfo(authHeader);

        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of(JSON_KEY_SUCCESS, false, JSON_KEY_ERROR, MSG_AUTHENTICATION_REQUIRED))
                .build();
        }

        UserInfo user = userOpt.get();

        try {
            boolean result = connectionManager.disconnect(user.userId()).get();
            return Response.ok(Map.of(JSON_KEY_SUCCESS, result, JSON_KEY_CONNECTED, false)).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.ok(Map.of(JSON_KEY_SUCCESS, false, JSON_KEY_ERROR, e.getMessage())).build();
        } catch (ExecutionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return Response.ok(Map.of(JSON_KEY_SUCCESS, false, JSON_KEY_ERROR, c.getMessage())).build();
        }
    }

    @GET
    @Path("/subscriptions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSubscriptions(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        Optional<UserInfo> userOpt = jwtService.extractUserInfo(authHeader);

        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of(JSON_KEY_ERROR, MSG_AUTHENTICATION_REQUIRED))
                .build();
        }

        UserInfo user = userOpt.get();
        List<Subscription> subscriptions = subscriptionService.getActiveSubscriptionsByUsername(user.username());
        Map<String, Boolean> receiverStatus = connectionManager.getReceiverStatus(user.userId());

        List<SubscriptionSummary> result = subscriptions.stream().map(s -> new SubscriptionSummary(
            s.getSubscriptionId() != null ? s.getSubscriptionId() : "",
            s.getQueueName() != null ? s.getQueueName() : "",
            s.getStatus() != null ? s.getStatus() : "",
            s.getTopicId() != null ? s.getTopicId() : "",
            s.getCreatedAt() != null ? s.getCreatedAt().toString() : "",
            s.getQueueName() != null && receiverStatus.containsKey(s.getQueueName()),
            messageService.getMessageCountByUsername(s.getSubscriptionId(), user.username()),
            s.getRequestBody()
        )).toList();

        return Response.ok(result).build();
    }

    @DELETE
    @Path("/subscriptions/{subscriptionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteSubscription(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
            @PathParam("subscriptionId") String subscriptionId) {

        Optional<UserInfo> userOpt = jwtService.extractUserInfo(authHeader);
        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of(JSON_KEY_ERROR, MSG_AUTHENTICATION_REQUIRED))
                .build();
        }

        UserInfo user = userOpt.get();

        Subscription subscription = subscriptionRepository.findBySubscriptionId(subscriptionId).orElse(null);
        if (subscription == null || !user.username().equals(subscription.getUsername())) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of(JSON_KEY_ERROR, "Subscription not found"))
                .build();
        }

        subscriptionService.deleteSubscription(user.userId(), subscriptionId);

        int providerStatusCode = -1;
        String providerUrl = subscription.getProviderUrl();
        if (providerUrl != null && !providerUrl.isBlank()) {
            String token = authHeader.replace(BEARER_PREFIX, "");
            try {
                String url = providerUrl + "/swim/v1/subscriptions/" + subscriptionId;
                providerStatusCode = providerHttpClient.executeDelete(url, token).statusCode();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Failed to delete subscription on remote provider: {}", e.getMessage());
            } catch (Exception e) {
                log.warn("Failed to delete subscription on remote provider: {}", e.getMessage());
            }
        }

        return Response.ok(Map.of(
            "localDeleted", true,
            "subscriptionId", subscriptionId,
            "providerStatusCode", providerStatusCode
        )).build();
    }

    @GET
    @Path("/messages/{subscriptionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMessages(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
            @PathParam("subscriptionId") String subscriptionId) {

        Optional<UserInfo> userOpt = jwtService.extractUserInfo(authHeader);

        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of(JSON_KEY_ERROR, MSG_AUTHENTICATION_REQUIRED))
                .build();
        }

        UserInfo user = userOpt.get();

        Subscription subscription = subscriptionRepository.findBySubscriptionId(subscriptionId).orElse(null);
        if (subscription == null || !user.username().equals(subscription.getUsername())) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of(JSON_KEY_ERROR, "Access denied to this subscription"))
                .build();
        }

        List<ReceivedMessage> messages = messageService.getMessagesBySubscription(subscriptionId);
        List<ReceivedMessageDto> messagesDto = messages.stream()
            .map(ReceivedMessageDto::from)
            .toList();
        return Response.ok(messagesDto).build();
    }

    @POST
    @Path("/test-queue")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response testQueueAccess(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
            QueueTestRequest request) {

        Optional<UserInfo> userOpt = jwtService.extractUserInfo(authHeader);

        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of(JSON_KEY_SUCCESS, false, JSON_KEY_ERROR, MSG_AUTHENTICATION_REQUIRED))
                .build();
        }

        UserInfo user = userOpt.get();

        if (!connectionManager.isConnected(user.userId())) {
            return Response.ok(Map.of(JSON_KEY_SUCCESS, false, JSON_KEY_ERROR, "Not connected to broker")).build();
        }

        String token = authHeader.replace(BEARER_PREFIX, "");

        try {
            String result = connectionManager.testQueueAccess(user.userId(), request.queueName(), token).get();
            if (result == null) {
                return Response.ok(Map.of(JSON_KEY_SUCCESS, true, "queueName", request.queueName())).build();
            } else {
                return Response.ok(Map.of(JSON_KEY_SUCCESS, false, JSON_KEY_ERROR, result)).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.ok(Map.of(JSON_KEY_SUCCESS, false, JSON_KEY_ERROR, e.getMessage())).build();
        } catch (ExecutionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return Response.ok(Map.of(JSON_KEY_SUCCESS, false, JSON_KEY_ERROR, c.getMessage())).build();
        }
    }
}
