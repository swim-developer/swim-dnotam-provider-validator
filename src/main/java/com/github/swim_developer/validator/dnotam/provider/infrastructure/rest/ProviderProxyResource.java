package com.github.swim_developer.validator.dnotam.provider.infrastructure.rest;

import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConsoleNotificationPort;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ProviderSubscriptionPort;
import com.github.swim_developer.validator.provider.infrastructure.rest.dto.UserInfo;
import com.github.swim_developer.validator.provider.infrastructure.security.JwtService;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import com.github.swim_developer.validator.dnotam.provider.infrastructure.client.ProviderHttpClient;

@Slf4j
@Path("/api/provider")
public class ProviderProxyResource {

    @SuppressWarnings("java:S1075")
    private static final String SWIM_V1_SUBSCRIPTIONS_PATH = "/swim/v1/subscriptions";

    private final ConsoleNotificationPort consoleService;
    private final ProviderSubscriptionPort subscriptionService;
    private final JwtService jwtService;
    private final ProviderHttpClient httpClient;

    @Inject
    public ProviderProxyResource(
            ConsoleNotificationPort consoleService,
            ProviderSubscriptionPort subscriptionService,
            JwtService jwtService,
            ProviderHttpClient httpClient) {
        this.consoleService = consoleService;
        this.subscriptionService = subscriptionService;
        this.jwtService = jwtService;
        this.httpClient = httpClient;
    }

    @POST
    @Path("/subscriptions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createSubscription(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
            @QueryParam("providerUrl") String providerUrl,
            String body) {
        String token = extractToken(authHeader);
        try {
            HttpResponse<Buffer> response = httpClient.executePost(providerUrl + SWIM_V1_SUBSCRIPTIONS_PATH, token, body);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                UserInfo user = extractUserInfo(token);
                subscriptionService.createSubscription(response.bodyAsString(), user.userId(), user.username(), providerUrl, body);
            }
            return httpClient.buildResponse(response);
        } catch (Exception e) {
            return handleError("POST /subscriptions", e, authHeader);
        }
    }

    @GET
    @Path("/subscriptions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listSubscriptions(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
            @QueryParam("providerUrl") String providerUrl) {
        String token = extractToken(authHeader);
        try {
            return httpClient.buildResponse(httpClient.executeGet(providerUrl + SWIM_V1_SUBSCRIPTIONS_PATH, token));
        } catch (Exception e) {
            return handleError("GET /subscriptions", e, authHeader);
        }
    }

    @GET
    @Path("/subscriptions/{subscriptionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSubscription(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
            @QueryParam("providerUrl") String providerUrl,
            @PathParam("subscriptionId") String subscriptionId) {
        String token = extractToken(authHeader);
        try {
            return httpClient.buildResponse(httpClient.executeGet(providerUrl + SWIM_V1_SUBSCRIPTIONS_PATH + "/" + subscriptionId, token));
        } catch (Exception e) {
            return handleError("GET /subscriptions/" + subscriptionId, e, authHeader);
        }
    }

    @PUT
    @Path("/subscriptions/{subscriptionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateSubscription(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
            @QueryParam("providerUrl") String providerUrl,
            @PathParam("subscriptionId") String subscriptionId,
            String body) {
        String token = extractToken(authHeader);
        try {
            HttpResponse<Buffer> response = httpClient.executePut(providerUrl + SWIM_V1_SUBSCRIPTIONS_PATH + "/" + subscriptionId, token, body);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                UserInfo user = extractUserInfo(token);
                subscriptionService.updateSubscription(response.bodyAsString(), user.userId(), subscriptionId);
            }
            return httpClient.buildResponse(response);
        } catch (Exception e) {
            return handleError("PUT /subscriptions/" + subscriptionId, e, authHeader);
        }
    }

    @DELETE
    @Path("/subscriptions/{subscriptionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteSubscription(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
            @QueryParam("providerUrl") String providerUrl,
            @PathParam("subscriptionId") String subscriptionId) {
        String token = extractToken(authHeader);
        try {
            HttpResponse<Buffer> response = httpClient.executeDelete(providerUrl + SWIM_V1_SUBSCRIPTIONS_PATH + "/" + subscriptionId, token);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                UserInfo user = extractUserInfo(token);
                subscriptionService.deleteSubscription(user.userId(), subscriptionId);
            }
            return httpClient.buildResponse(response);
        } catch (Exception e) {
            return handleError("DELETE /subscriptions/" + subscriptionId, e, authHeader);
        }
    }

    @GET
    @Path("/topics")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listTopics(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
            @QueryParam("providerUrl") String providerUrl) {
        String token = extractToken(authHeader);
        try {
            return httpClient.buildResponse(httpClient.executeGet(providerUrl + "/swim/v1/topics", token));
        } catch (Exception e) {
            return handleError("GET /topics", e, authHeader);
        }
    }

    @GET
    @Path("/topics/{topicId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTopic(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
            @QueryParam("providerUrl") String providerUrl,
            @PathParam("topicId") String topicId) {
        String token = extractToken(authHeader);
        try {
            return httpClient.buildResponse(httpClient.executeGet(providerUrl + "/swim/v1/topics/" + topicId, token));
        } catch (Exception e) {
            return handleError("GET /topics/" + topicId, e, authHeader);
        }
    }

    @GET
    @Path("/features")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFeatures(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
            @QueryParam("providerUrl") String providerUrl) {
        String token = extractToken(authHeader);
        try {
            return httpClient.buildResponse(httpClient.executeGet(providerUrl + "/swim/v1/features", token));
        } catch (Exception e) {
            return handleError("GET /features", e, authHeader);
        }
    }

    @POST
    @Path("/features")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response queryFeatures(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
            @QueryParam("providerUrl") String providerUrl,
            String body) {
        String token = extractToken(authHeader);
        try {
            return httpClient.buildResponse(httpClient.executePost(providerUrl + "/swim/v1/features", token, body));
        } catch (Exception e) {
            return handleError("POST /features", e, authHeader);
        }
    }

    private Response handleError(String operation, Exception e, String authHeader) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        log.error("Provider proxy error: {} - {}", operation, e.getMessage());
        String userId = jwtService.extractUserInfo(authHeader).map(UserInfo::userId).orElse(null);
        consoleService.error(userId, "Provider error: " + e.getMessage());
        return Response.status(Response.Status.BAD_GATEWAY)
                .entity(Map.of("status", 502, "error", e.getMessage()))
                .build();
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }

    private UserInfo extractUserInfo(String token) {
        return jwtService.parseToken(token).orElse(new UserInfo("unknown", "unknown", token));
    }
}
