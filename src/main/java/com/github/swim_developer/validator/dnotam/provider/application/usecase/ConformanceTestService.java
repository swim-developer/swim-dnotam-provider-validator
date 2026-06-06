package com.github.swim_developer.validator.dnotam.provider.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.swim_developer.validator.provider.domain.model.AssertionResult;
import com.github.swim_developer.validator.provider.domain.model.TestOutcome;
import com.github.swim_developer.validator.provider.domain.model.TestResult;
import com.github.swim_developer.validator.provider.domain.model.TestScenario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import com.github.swim_developer.validator.dnotam.provider.domain.model.HttpResult;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConformanceHttpPort;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConformanceTestPort;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConsoleNotificationPort;

@Slf4j
@ApplicationScoped
public class ConformanceTestService implements ConformanceTestPort {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private static final Pattern QUEUE_NAME_PATTERN = Pattern.compile(
            "^DNOTAM-[^-]+-[a-f0-9\\-]+$"
    );

    private static final String SCENARIO_API_01 = "API-01";
    private static final String SCENARIO_API_02 = "API-02";
    private static final String SCENARIO_API_06 = "API-06";
    private static final String SCENARIO_API_09 = "API-09";
    private static final String SCENARIO_DM_01 = "DM-01";
    private static final String SCENARIO_DM_04 = "DM-04";
    private static final String SCENARIO_DM_05 = "DM-05";
    private static final String SCENARIO_DM_06 = "DM-06";
    @SuppressWarnings("java:S1075")
    private static final String PATH_SWIM_V1_SUBSCRIPTIONS = "/swim/v1/subscriptions";
    private static final String JSON_MINIMAL_SUBSCRIPTION = "{\"topic\": \"DigitalNOTAMService\"}";
    private static final String FIELD_SUBSCRIPTION_ID = "subscription_id";
    private static final String FIELD_SUBSCRIPTION_STATUS = "subscription_status";
    private static final String FIELD_QUEUE = "queue";
    private static final String TEST_NAME_GET_SUBSCRIPTIONS_BY_ID = "GET /subscriptions/{id}";
    private static final String ASSERTION_NAME_SUBSCRIPTION_ID_UUID = "subscription_id is UUID";
    private static final String ASSERTION_NAME_QUEUE_NAMING = "Queue naming pattern";

    private final ObjectMapper objectMapper;
    private final ConsoleNotificationPort consoleService;
    private final ConformanceAssertions assertions;
    private final ConformanceHttpPort httpClient;

    @Inject
    public ConformanceTestService(
            ObjectMapper objectMapper,
            ConsoleNotificationPort consoleService,
            ConformanceAssertions assertions,
            ConformanceHttpPort httpClient) {
        this.objectMapper = objectMapper;
        this.consoleService = consoleService;
        this.assertions = assertions;
        this.httpClient = httpClient;
    }

    public TestResult executeScenario(TestScenario scenario, String providerUrl, String authToken, String userId) {
        Instant start = Instant.now();
        try {
            consoleService.info(userId, "Executing test: " + scenario.id() + " - " + scenario.name());

            return switch (scenario.id()) {
                case SCENARIO_API_01 -> testCreateSubscriptionHappyPath(providerUrl, authToken, start);
                case SCENARIO_API_02 -> testCreateSubscriptionMinimal(providerUrl, authToken, start);
                case SCENARIO_API_06 -> testListSubscriptions(providerUrl, authToken, start);
                case SCENARIO_API_09 -> testGetSubscriptionById(providerUrl, authToken, start);
                case SCENARIO_DM_01 -> testRequiredFieldsPresent(providerUrl, authToken, start);
                case SCENARIO_DM_04 -> testSubscriptionIdIsUUID(providerUrl, authToken, start);
                case SCENARIO_DM_05 -> testQueueNamingPattern(providerUrl, authToken, start);
                case SCENARIO_DM_06 -> testInitialStatusIsPaused(providerUrl, authToken, start);
                default -> new TestResult(scenario.id(), scenario.name(), TestOutcome.SKIP,
                        Instant.now(), 0L, "Test not yet implemented", List.of(), providerUrl, null, null, null);
            };
        } catch (Exception e) {
            long duration = Instant.now().toEpochMilli() - start.toEpochMilli();
            log.error("Test execution failed: {}", scenario.id(), e);
            consoleService.error(userId, "Test failed: " + scenario.id() + " - " + e.getMessage());
            return new TestResult(scenario.id(), scenario.name(), TestOutcome.ERROR,
                    Instant.now(), duration, e.getMessage(), List.of(), providerUrl, null, null, null);
        }
    }

    private TestResult testCreateSubscriptionHappyPath(String providerUrl, String authToken, Instant start) {
        String requestBody = """
                {
                    "topic": "DigitalNOTAMService",
                    "eventScenario": ["RWY.CLS", "AD.CLS"],
                    "airportHeliport": ["EHAM", "LFPG"],
                    "airspace": ["EHAA"],
                    "eventSeries": "A",
                    "publisher": "EUROCONTROL",
                    "description": "Test subscription with all optional fields",
                    "comment": "Conformance test"
                }
                """;

        HttpResult response = httpClient.executePost(providerUrl + PATH_SWIM_V1_SUBSCRIPTIONS, authToken, requestBody);
        long duration = elapsed(start);
        List<AssertionResult> results = new ArrayList<>();
        TestOutcome outcome = TestOutcome.PASS;

        results.add(assertions.assertStatusCode(response, 201));
        if (response.statusCode() != 201) {
            outcome = TestOutcome.FAIL;
        }

        try {
            JsonNode json = objectMapper.readTree(response.body());
            results.add(assertions.assertFieldPresent(json, FIELD_SUBSCRIPTION_ID));
            results.add(assertions.assertFieldEquals(json, FIELD_SUBSCRIPTION_STATUS, "PAUSED"));
            results.add(assertions.assertArrayFieldPresent(json, "eventScenario"));
            results.add(assertions.assertArrayFieldPresent(json, "airportHeliport"));
            results.add(assertions.assertArrayFieldPresent(json, "airspace"));
            results.add(assertions.assertFieldPresent(json, "eventSeries"));
            results.add(assertions.assertFieldPresent(json, "publisher"));
            results.add(assertions.assertFieldPresent(json, "comment"));
            results.add(assertions.assertFieldPresent(json, "description"));

            if (results.stream().anyMatch(a -> !a.passed())) {
                outcome = TestOutcome.FAIL;
            }
            return new TestResult(SCENARIO_API_01, "POST /subscriptions - happy path", outcome,
                    Instant.now(), duration, null, results, providerUrl, requestBody, response.body(), response.statusCode());
        } catch (Exception e) {
            return new TestResult(SCENARIO_API_01, "POST /subscriptions - happy path", TestOutcome.ERROR,
                    Instant.now(), duration, "Failed to parse response: " + e.getMessage(),
                    results, providerUrl, requestBody, response.body(), response.statusCode());
        }
    }

    private TestResult testCreateSubscriptionMinimal(String providerUrl, String authToken, Instant start) {
        String requestBody = JSON_MINIMAL_SUBSCRIPTION;
        HttpResult response = httpClient.executePost(providerUrl + PATH_SWIM_V1_SUBSCRIPTIONS, authToken, requestBody);
        long duration = elapsed(start);
        List<AssertionResult> results = new ArrayList<>();
        results.add(assertions.assertStatusCode(response, 201));

        try {
            JsonNode json = objectMapper.readTree(response.body());
            results.add(assertions.assertFieldPresent(json, FIELD_SUBSCRIPTION_ID));
            results.add(assertions.assertFieldPresent(json, FIELD_QUEUE));
            results.add(assertions.assertFieldEquals(json, "qos", "AT_LEAST_ONCE"));
            results.add(assertions.assertFieldEquals(json, "durable", true));
            TestOutcome outcome = results.stream().anyMatch(a -> !a.passed()) ? TestOutcome.FAIL : TestOutcome.PASS;
            return new TestResult(SCENARIO_API_02, "POST /subscriptions - minimal request", outcome,
                    Instant.now(), duration, null, results, providerUrl, requestBody, response.body(), response.statusCode());
        } catch (Exception e) {
            return new TestResult(SCENARIO_API_02, "POST /subscriptions - minimal request", TestOutcome.ERROR,
                    Instant.now(), duration, e.getMessage(), results, providerUrl, requestBody, response.body(), response.statusCode());
        }
    }

    private TestResult testListSubscriptions(String providerUrl, String authToken, Instant start) {
        HttpResult response = httpClient.executeGet(providerUrl + PATH_SWIM_V1_SUBSCRIPTIONS, authToken);
        long duration = elapsed(start);
        List<AssertionResult> results = new ArrayList<>();
        results.add(assertions.assertStatusCode(response, 200));

        try {
            JsonNode json = objectMapper.readTree(response.body());
            results.add(new AssertionResult("Response is array", json.isArray(),
                    "JSON array", json.getNodeType().toString(),
                    json.isArray() ? "Response is array" : "Response is not array"));
            TestOutcome outcome = results.stream().anyMatch(a -> !a.passed()) ? TestOutcome.FAIL : TestOutcome.PASS;
            return new TestResult(SCENARIO_API_06, "GET /subscriptions - list all", outcome,
                    Instant.now(), duration, null, results, providerUrl, null, response.body(), response.statusCode());
        } catch (Exception e) {
            return new TestResult(SCENARIO_API_06, "GET /subscriptions - list all", TestOutcome.ERROR,
                    Instant.now(), duration, e.getMessage(), results, providerUrl, null, response.body(), response.statusCode());
        }
    }

    private TestResult testGetSubscriptionById(String providerUrl, String authToken, Instant start) {
        String createBody = JSON_MINIMAL_SUBSCRIPTION;
        HttpResult createResponse = httpClient.executePost(providerUrl + PATH_SWIM_V1_SUBSCRIPTIONS, authToken, createBody);

        if (createResponse.statusCode() != 201) {
            long duration = elapsed(start);
            return new TestResult(SCENARIO_API_09, TEST_NAME_GET_SUBSCRIPTIONS_BY_ID, TestOutcome.ERROR,
                    Instant.now(), duration, "Failed to create test subscription: " + createResponse.statusCode(),
                    List.of(), providerUrl, null, null, createResponse.statusCode());
        }

        try {
            JsonNode createJson = objectMapper.readTree(createResponse.body());
            String subscriptionId = createJson.has(FIELD_SUBSCRIPTION_ID)
                    ? createJson.get(FIELD_SUBSCRIPTION_ID).asText()
                    : createJson.get("subscriptionId").asText();

            HttpResult response = httpClient.executeGet(
                    providerUrl + PATH_SWIM_V1_SUBSCRIPTIONS + "/" + subscriptionId, authToken);
            long duration = elapsed(start);
            List<AssertionResult> results = new ArrayList<>();
            results.add(assertions.assertStatusCode(response, 200));

            JsonNode json = objectMapper.readTree(response.body());
            results.add(assertions.assertFieldPresent(json, FIELD_SUBSCRIPTION_ID));
            results.add(assertions.assertFieldPresent(json, "topic"));
            results.add(assertions.assertFieldPresent(json, FIELD_QUEUE));

            TestOutcome outcome = results.stream().anyMatch(a -> !a.passed()) ? TestOutcome.FAIL : TestOutcome.PASS;
            return new TestResult(SCENARIO_API_09, TEST_NAME_GET_SUBSCRIPTIONS_BY_ID, outcome,
                    Instant.now(), duration, null, results, providerUrl, null, response.body(), response.statusCode());
        } catch (Exception e) {
            long duration = elapsed(start);
            return new TestResult(SCENARIO_API_09, TEST_NAME_GET_SUBSCRIPTIONS_BY_ID, TestOutcome.ERROR,
                    Instant.now(), duration, e.getMessage(), List.of(), providerUrl, null, null, null);
        }
    }

    private TestResult testRequiredFieldsPresent(String providerUrl, String authToken, Instant start) {
        String requestBody = JSON_MINIMAL_SUBSCRIPTION;
        HttpResult response = httpClient.executePost(providerUrl + PATH_SWIM_V1_SUBSCRIPTIONS, authToken, requestBody);
        long duration = elapsed(start);
        List<AssertionResult> results = new ArrayList<>();

        try {
            JsonNode json = objectMapper.readTree(response.body());
            results.add(assertions.assertFieldPresent(json, "topic"));
            results.add(assertions.assertFieldPresent(json, FIELD_SUBSCRIPTION_ID));
            results.add(assertions.assertFieldPresent(json, FIELD_QUEUE));
            results.add(assertions.assertFieldPresent(json, FIELD_SUBSCRIPTION_STATUS));
            results.add(assertions.assertFieldPresent(json, "qos"));
            results.add(assertions.assertFieldPresent(json, "durable"));
            results.add(assertions.assertFieldPresent(json, "subscription_end"));
            results.add(assertions.assertFieldPresent(json, "provider_name"));
            results.add(assertions.assertFieldPresent(json, "heartbeat_queue"));
            TestOutcome outcome = results.stream().anyMatch(a -> !a.passed()) ? TestOutcome.FAIL : TestOutcome.PASS;
            return new TestResult(SCENARIO_DM_01, "Required fields present", outcome,
                    Instant.now(), duration, null, results, providerUrl, requestBody, response.body(), response.statusCode());
        } catch (Exception e) {
            return new TestResult(SCENARIO_DM_01, "Required fields present", TestOutcome.ERROR,
                    Instant.now(), duration, e.getMessage(), results, providerUrl, requestBody, response.body(), response.statusCode());
        }
    }

    private TestResult testSubscriptionIdIsUUID(String providerUrl, String authToken, Instant start) {
        String requestBody = JSON_MINIMAL_SUBSCRIPTION;
        HttpResult response = httpClient.executePost(providerUrl + PATH_SWIM_V1_SUBSCRIPTIONS, authToken, requestBody);
        long duration = elapsed(start);
        List<AssertionResult> results = new ArrayList<>();

        try {
            JsonNode json = objectMapper.readTree(response.body());
            String subscriptionId = json.has(FIELD_SUBSCRIPTION_ID)
                    ? json.get(FIELD_SUBSCRIPTION_ID).asText()
                    : json.get("subscriptionId").asText();
            boolean isUuid = UUID_PATTERN.matcher(subscriptionId).matches();
            results.add(new AssertionResult(ASSERTION_NAME_SUBSCRIPTION_ID_UUID, isUuid, "UUID format", subscriptionId,
                    isUuid ? "Valid UUID format" : "Invalid UUID format"));
            TestOutcome outcome = isUuid ? TestOutcome.PASS : TestOutcome.FAIL;
            return new TestResult(SCENARIO_DM_04, ASSERTION_NAME_SUBSCRIPTION_ID_UUID, outcome,
                    Instant.now(), duration, null, results, providerUrl, requestBody, response.body(), response.statusCode());
        } catch (Exception e) {
            return new TestResult(SCENARIO_DM_04, ASSERTION_NAME_SUBSCRIPTION_ID_UUID, TestOutcome.ERROR,
                    Instant.now(), duration, e.getMessage(), results, providerUrl, requestBody, response.body(), response.statusCode());
        }
    }

    private TestResult testQueueNamingPattern(String providerUrl, String authToken, Instant start) {
        String requestBody = JSON_MINIMAL_SUBSCRIPTION;
        HttpResult response = httpClient.executePost(providerUrl + PATH_SWIM_V1_SUBSCRIPTIONS, authToken, requestBody);
        long duration = elapsed(start);
        List<AssertionResult> results = new ArrayList<>();

        try {
            JsonNode json = objectMapper.readTree(response.body());
            String queue = json.has(FIELD_QUEUE) ? json.get(FIELD_QUEUE).asText() : json.get("queueName").asText();
            boolean matchesPattern = QUEUE_NAME_PATTERN.matcher(queue).matches();
            results.add(new AssertionResult(ASSERTION_NAME_QUEUE_NAMING, matchesPattern,
                    "DNOTAM-<userId>-<uuid>", queue,
                    matchesPattern ? "Matches EUR SWIM Registry pattern" : "Does not match pattern"));
            TestOutcome outcome = matchesPattern ? TestOutcome.PASS : TestOutcome.FAIL;
            return new TestResult(SCENARIO_DM_05, ASSERTION_NAME_QUEUE_NAMING, outcome,
                    Instant.now(), duration, null, results, providerUrl, requestBody, response.body(), response.statusCode());
        } catch (Exception e) {
            return new TestResult(SCENARIO_DM_05, ASSERTION_NAME_QUEUE_NAMING, TestOutcome.ERROR,
                    Instant.now(), duration, e.getMessage(), results, providerUrl, requestBody, response.body(), response.statusCode());
        }
    }

    private TestResult testInitialStatusIsPaused(String providerUrl, String authToken, Instant start) {
        String requestBody = JSON_MINIMAL_SUBSCRIPTION;
        HttpResult response = httpClient.executePost(providerUrl + PATH_SWIM_V1_SUBSCRIPTIONS, authToken, requestBody);
        long duration = elapsed(start);
        List<AssertionResult> results = new ArrayList<>();
        results.add(assertions.assertStatusCode(response, 201));

        try {
            JsonNode json = objectMapper.readTree(response.body());
            results.add(assertions.assertFieldEquals(json, FIELD_SUBSCRIPTION_STATUS, "PAUSED"));
            TestOutcome outcome = results.stream().anyMatch(a -> !a.passed()) ? TestOutcome.FAIL : TestOutcome.PASS;
            return new TestResult(SCENARIO_DM_06, "Initial status is PAUSED", outcome,
                    Instant.now(), duration, null, results, providerUrl, requestBody, response.body(), response.statusCode());
        } catch (Exception e) {
            return new TestResult(SCENARIO_DM_06, "Initial status is PAUSED", TestOutcome.ERROR,
                    Instant.now(), duration, e.getMessage(), results, providerUrl, requestBody, response.body(), response.statusCode());
        }
    }

    private long elapsed(Instant start) {
        return Instant.now().toEpochMilli() - start.toEpochMilli();
    }
}
