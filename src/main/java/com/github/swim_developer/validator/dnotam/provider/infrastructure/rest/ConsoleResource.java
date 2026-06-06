package com.github.swim_developer.validator.dnotam.provider.infrastructure.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.swim_developer.validator.dnotam.provider.application.port.in.ConsoleStreamPort;
import com.github.swim_developer.validator.provider.infrastructure.rest.dto.UserInfo;
import com.github.swim_developer.validator.provider.infrastructure.security.JwtService;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.time.Duration;

@Slf4j
@Path("/api/console")
public class ConsoleResource {

    private final ConsoleStreamPort consoleService;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;

    @Inject
    public ConsoleResource(ConsoleStreamPort consoleService, ObjectMapper objectMapper, JwtService jwtService) {
        this.consoleService = consoleService;
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
    }

    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<String> consoleStream(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("token") String token) {
        String effectiveAuth;
        if (authHeader != null) {
            effectiveAuth = authHeader;
        } else if (token != null) {
            effectiveAuth = "Bearer " + token;
        } else {
            effectiveAuth = null;
        }
        String userId = jwtService.extractUserInfo(effectiveAuth)
                .map(UserInfo::userId).orElse(null);

        Multi<String> heartbeat = Multi.createFrom()
                .ticks().every(Duration.ofSeconds(30))
                .map(tick -> "{\"type\":\"heartbeat\"}");

        Multi<String> events = consoleService.streamForUser(userId)
                .map(entry -> {
                    try {
                        return objectMapper.writeValueAsString(entry);
                    } catch (JsonProcessingException e) {
                        return "{\"type\":\"error\",\"message\":\"Serialization error\"}";
                    }
                });

        return Multi.createBy().merging().streams(heartbeat, events)
                .onFailure().recoverWithCompletion();
    }
}
