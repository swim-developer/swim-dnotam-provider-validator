package com.github.swim_developer.validator.dnotam.provider.infrastructure.rest;

import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;
import com.github.swim_developer.validator.dnotam.provider.domain.port.out.ReceivedMessageRepository;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;

@Path("/ui")
public class UiResource {

    private static final String QUTE_DATA_TITLE = "title";
    private static final String QUTE_DATA_ACTIVE = "active";

    private final Template dashboard;
    private final Template api;
    private final Template token;
    private final Template console;
    private final Template amqp;
    private final Template messageDetail;
    private final Template subscriptions;
    private final Template testScenarios;
    private final Template businessMessages;
    private final Template controlMessages;
    private final ReceivedMessageRepository messageRepository;

    @Inject
    public UiResource(
            Template dashboard,
            Template api,
            Template token,
            Template console,
            Template amqp,
            Template messageDetail,
            Template subscriptions,
            Template testScenarios,
            @Location("business-messages") Template businessMessages,
            @Location("control-messages") Template controlMessages,
            ReceivedMessageRepository messageRepository) {
        this.dashboard = dashboard;
        this.api = api;
        this.token = token;
        this.console = console;
        this.amqp = amqp;
        this.messageDetail = messageDetail;
        this.subscriptions = subscriptions;
        this.testScenarios = testScenarios;
        this.businessMessages = businessMessages;
        this.controlMessages = controlMessages;
        this.messageRepository = messageRepository;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance dashboard() {
        return dashboard
                .data(QUTE_DATA_TITLE, "Dashboard - SWIM DNOTAM Provider Validator")
                .data(QUTE_DATA_ACTIVE, "dashboard")
                .data("authenticated", false);
    }

    @GET
    @Path("/api")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance api() {
        return api.data(QUTE_DATA_TITLE, "Provider API", QUTE_DATA_ACTIVE, "api");
    }

    @GET
    @Path("/token")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance token() {
        return token
                .data(QUTE_DATA_TITLE, "Token - SWIM DNOTAM Provider Validator")
                .data(QUTE_DATA_ACTIVE, "token")
                .data("authenticated", false);
    }

    @GET
    @Path("/console")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance console() {
        return console.data(QUTE_DATA_ACTIVE, "console");
    }

    @GET
    @Path("/amqp")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance amqp() {
        return amqp.data(QUTE_DATA_ACTIVE, "amqp");
    }

    @GET
    @Path("/messages")
    public Response messages() {
        return Response.seeOther(URI.create("/ui/messages/business")).build();
    }

    @GET
    @Path("/messages/business")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance businessMessages() {
        return businessMessages
                .data(QUTE_DATA_TITLE, "Business Messages - SWIM DNOTAM Provider Validator")
                .data(QUTE_DATA_ACTIVE, "business-messages");
    }

    @GET
    @Path("/messages/control")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance controlMessages() {
        return controlMessages
                .data(QUTE_DATA_TITLE, "Control Messages - SWIM DNOTAM Provider Validator")
                .data(QUTE_DATA_ACTIVE, "control-messages");
    }

    @GET
    @Path("/messages/{id}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance messageDetail(@PathParam("id") Long id) {
        ReceivedMessage message = messageRepository.findMessageById(id);
        return messageDetail
            .data(QUTE_DATA_ACTIVE, "business-messages")
            .data("message", message);
    }

    @GET
    @Path("/subscriptions")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance subscriptions() {
        return subscriptions.data(QUTE_DATA_ACTIVE, "subscriptions");
    }

    @GET
    @Path("/test-scenarios")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance testScenarios() {
        return testScenarios
                .data(QUTE_DATA_TITLE, "Conformance Testing - SWIM DNOTAM Provider Validator")
                .data(QUTE_DATA_ACTIVE, "test-scenarios");
    }
}

