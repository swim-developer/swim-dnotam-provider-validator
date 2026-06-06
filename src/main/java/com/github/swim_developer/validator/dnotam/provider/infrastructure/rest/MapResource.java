package com.github.swim_developer.validator.dnotam.provider.infrastructure.rest;

import com.github.swim_developer.validator.dnotam.provider.domain.port.in.MapQueryPort;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.MessagePort;
import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;
import com.github.swim_developer.validator.dnotam.provider.domain.port.out.MapRenderPort;
import com.github.swim_developer.validator.provider.infrastructure.rest.dto.UserInfo;
import com.github.swim_developer.validator.provider.infrastructure.security.JwtService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Path("/api/map")
public class MapResource {

    private final MapRenderPort mapRenderer;
    private final MapQueryPort eventMapQueryService;
    private final MessagePort messageService;
    private final JwtService jwtService;
    private final ApiHtmlHelper htmlHelper;

    @Inject
    public MapResource(
            MapRenderPort mapRenderer,
            MapQueryPort eventMapQueryService,
            MessagePort messageService,
            JwtService jwtService,
            ApiHtmlHelper htmlHelper) {
        this.mapRenderer = mapRenderer;
        this.eventMapQueryService = eventMapQueryService;
        this.messageService = messageService;
        this.jwtService = jwtService;
        this.htmlHelper = htmlHelper;
    }

    @GET
    @Path("/events")
    @Produces("image/svg+xml")
    public Response getEventsMap(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("limit") Integer limit,
            @QueryParam("minutes") Integer minutes) {
        Optional<UserInfo> userOpt = jwtService.extractUserFromHeader(authHeader);
        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        UserInfo user = userOpt.get();

        List<ReceivedMessage> userMessages;
        if (minutes != null && minutes > 0) {
            userMessages = messageService.getMessagesByUsernameAfter(user.username(), LocalDateTime.now().minusMinutes(minutes));
        } else {
            userMessages = messageService.getMessagesByUsername(user.username());
        }

        if (limit != null && limit > 0 && userMessages.size() > limit) {
            userMessages = userMessages.subList(0, limit);
        }

        return Response.ok(mapRenderer.generateMapWithEvents(userMessages))
                .header("Cache-Control", "no-cache")
                .build();
    }

    @GET
    @Path("/events.html")
    @Produces(MediaType.TEXT_HTML)
    public Response getEventsMapHtml(@QueryParam("limit") Integer limit) {
        String svg;
        if (limit != null && limit > 0) {
            svg = eventMapQueryService.generateMapWithRecentEvents(limit);
        } else {
            svg = eventMapQueryService.generateMapWithAllEvents();
        }
        return Response.ok(htmlHelper.buildEventsMapHtml(svg)).build();
    }
}
