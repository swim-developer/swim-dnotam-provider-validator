package com.github.swim_developer.validator.dnotam.provider.infrastructure.rest;

import com.github.swim_developer.validator.provider.domain.model.TestOutcome;
import com.github.swim_developer.validator.provider.domain.model.TestResult;
import com.github.swim_developer.validator.provider.domain.model.TestScenario;
import com.github.swim_developer.validator.provider.infrastructure.rest.dto.UserInfo;
import com.github.swim_developer.validator.provider.infrastructure.security.JwtService;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConformanceTestPort;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.TestScenarioPort;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@Path("/api/conformance")
@Produces(MediaType.APPLICATION_JSON)
public class ConformanceTestResource {

    private static final String JSON_KEY_ERROR = "error";

    private final TestScenarioPort scenarioRegistry;
    private final ConformanceTestPort conformanceTestService;
    private final JwtService jwtService;

    @Inject
    public ConformanceTestResource(
            TestScenarioPort scenarioRegistry,
            ConformanceTestPort conformanceTestService,
            JwtService jwtService) {
        this.scenarioRegistry = scenarioRegistry;
        this.conformanceTestService = conformanceTestService;
        this.jwtService = jwtService;
    }

    @GET
    @Path("/scenarios")
    public Response listScenarios(@QueryParam("category") String category,
                                   @QueryParam("implemented") Boolean implementedOnly) {
        List<TestScenario> scenarios;

        if (implementedOnly != null && implementedOnly) {
            scenarios = scenarioRegistry.getImplementedScenarios();
        } else if (category != null) {
            scenarios = scenarioRegistry.getScenariosByCategory(category);
        } else {
            scenarios = scenarioRegistry.getAllScenarios();
        }

        return Response.ok(scenarios).build();
    }

    @GET
    @Path("/scenarios/{scenarioId}")
    public Response getScenario(@PathParam("scenarioId") String scenarioId) {
        return scenarioRegistry.getScenario(scenarioId)
            .map(scenario -> Response.ok(scenario).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of(JSON_KEY_ERROR, "Scenario not found: " + scenarioId))
                .build());
    }

    @POST
    @Path("/test")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response executeTest(
            @QueryParam("scenarioId") String scenarioId,
            @QueryParam("providerUrl") String providerUrl,
            @HeaderParam("Authorization") String authHeader) {

        if (scenarioId == null || scenarioId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(JSON_KEY_ERROR, "scenarioId is required"))
                .build();
        }

        if (providerUrl == null || providerUrl.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(JSON_KEY_ERROR, "providerUrl is required"))
                .build();
        }

        TestScenario scenario = scenarioRegistry.getScenario(scenarioId).orElse(null);
        if (scenario == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of(JSON_KEY_ERROR, "Scenario not found: " + scenarioId))
                .build();
        }

        String token = extractToken(authHeader);
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of(JSON_KEY_ERROR, "Authorization token required"))
                .build();
        }

        try {
            String userId = jwtService.extractUserInfo(authHeader).map(UserInfo::userId).orElse(null);
            TestResult result = conformanceTestService.executeScenario(scenario, providerUrl, token, userId);
            return Response.ok(result).build();
        } catch (Exception e) {
            log.error("Test execution failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of(JSON_KEY_ERROR, "Test execution failed: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/test/suite")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response executeSuite(
            @QueryParam("suite") String suite,
            @QueryParam("providerUrl") String providerUrl,
            @HeaderParam("Authorization") String authHeader) {

        if (providerUrl == null || providerUrl.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(JSON_KEY_ERROR, "providerUrl is required"))
                .build();
        }

        String token = extractToken(authHeader);
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of(JSON_KEY_ERROR, "Authorization token required"))
                .build();
        }

        List<TestScenario> scenarios;
        if ("smoke".equalsIgnoreCase(suite)) {
            scenarios = List.of("API-01", "API-06", "API-09", "DM-01", "DM-06").stream()
                .map(scenarioRegistry::getScenario)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();
        } else if ("implemented".equalsIgnoreCase(suite)) {
            scenarios = scenarioRegistry.getImplementedScenarios();
        } else {
            scenarios = scenarioRegistry.getAllScenarios();
        }

        String userId = jwtService.extractUserInfo(authHeader).map(UserInfo::userId).orElse(null);
        List<TestResult> results = scenarios.stream()
            .map(scenario -> conformanceTestService.executeScenario(scenario, providerUrl, token, userId))
            .toList();

        return Response.ok(Map.of(
            "suite", suite != null ? suite : "all",
            "total", results.size(),
            "passed", results.stream().filter(r -> r.outcome() == TestOutcome.PASS).count(),
            "failed", results.stream().filter(r -> r.outcome() == TestOutcome.FAIL).count(),
            "errors", results.stream().filter(r -> r.outcome() == TestOutcome.ERROR).count(),
            "skipped", results.stream().filter(r -> r.outcome() == TestOutcome.SKIP).count(),
            "results", results
        )).build();
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        if (authHeader.toLowerCase().startsWith("bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }
}
