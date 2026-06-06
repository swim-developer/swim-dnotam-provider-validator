package com.github.swim_developer.validator.dnotam.provider.application.usecase;

import com.github.swim_developer.validator.provider.domain.model.TestScenario;
import com.github.swim_developer.validator.provider.domain.model.TestStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.TestScenarioPort;

@ApplicationScoped
public class TestScenarioRegistry implements TestScenarioPort {

    private static final String CATEGORY_REST_API = "REST API";
    private static final String CATEGORY_DATA_MODEL = "Data Model";

    private final List<TestScenario> scenarios = new ArrayList<>();

    public TestScenarioRegistry() {
        registerScenarios();
    }

    private void registerScenarios() {
        // REST API Conformance
        scenarios.add(new TestScenario(
            "API-01",
            "POST /subscriptions - happy path",
            CATEGORY_REST_API,
            "Send valid request with all optional fields",
            "Create subscription with eventScenario, airportHeliport, airspace, eventSeries, publisher, comment",
            "Response 201; subscription_status=PAUSED; all filter fields echoed",
            TestStatus.IMPLEMENTED
        ));

        scenarios.add(new TestScenario(
            "API-02",
            "POST /subscriptions - minimal request",
            CATEGORY_REST_API,
            "Send request with topic only",
            "Create subscription with only required topic field",
            "Response 201; defaults applied (qos=AT_LEAST_ONCE, durable=true); queue generated",
            TestStatus.IMPLEMENTED
        ));

        scenarios.add(new TestScenario(
            "API-06",
            "GET /subscriptions - list all",
            CATEGORY_REST_API,
            "Retrieve subscriptions list",
            "GET /swim/v1/subscriptions",
            "Response 200; JSON array returned",
            TestStatus.IMPLEMENTED
        ));

        scenarios.add(new TestScenario(
            "API-09",
            "GET /subscriptions/{id}",
            CATEGORY_REST_API,
            "Retrieve subscription by ID",
            "GET /swim/v1/subscriptions/{subscriptionId}",
            "Response 200; all fields present",
            TestStatus.IMPLEMENTED
        ));

        // Data Model Conformance
        scenarios.add(new TestScenario(
            "DM-01",
            "Required fields present",
            CATEGORY_DATA_MODEL,
            "Validate EUR SWIM Registry required fields",
            "Create subscription and inspect response schema",
            "topic, subscription_id, queue, subscription_status, qos, durable, subscription_end, provider_name, heartbeat_queue all present",
            TestStatus.IMPLEMENTED
        ));

        scenarios.add(new TestScenario(
            "DM-04",
            "subscription_id is UUID",
            CATEGORY_DATA_MODEL,
            "Validate subscription_id format",
            "Create subscription and check subscription_id pattern",
            "subscription_id matches UUID v4 regex",
            TestStatus.IMPLEMENTED
        ));

        scenarios.add(new TestScenario(
            "DM-05",
            "Queue naming pattern",
            CATEGORY_DATA_MODEL,
            "Validate queue naming follows EUR SWIM Registry pattern",
            "Create subscription with auto-generated queue",
            "queue matches DNOTAM-<userId>-<uuid>",
            TestStatus.IMPLEMENTED
        ));

        scenarios.add(new TestScenario(
            "DM-06",
            "Initial status is PAUSED",
            CATEGORY_DATA_MODEL,
            "Validate initial subscription status",
            "Create subscription",
            "subscription_status=PAUSED (EUR SWIM Registry requirement)",
            TestStatus.IMPLEMENTED
        ));

        // Roadmap scenarios (not yet implemented)
        scenarios.add(new TestScenario(
            "API-11",
            "PUT /subscriptions/{id} - PAUSED → ACTIVE",
            CATEGORY_REST_API,
            "Update subscription status to ACTIVE",
            "PUT /swim/v1/subscriptions/{id} with status=ACTIVE",
            "Response 200; subscription_status=ACTIVE",
            TestStatus.ROADMAP
        ));

        scenarios.add(new TestScenario(
            "API-17",
            "PUT /subscriptions/{id}/renew",
            CATEGORY_REST_API,
            "Renew subscription TTL",
            "PUT /swim/v1/subscriptions/{id}/renew",
            "Response 200; subscription_end extended by 30 days",
            TestStatus.ROADMAP
        ));

        scenarios.add(new TestScenario(
            "DM-02",
            "Filter fields echoed in response",
            CATEGORY_DATA_MODEL,
            "Validate filter echo for reconciliation",
            "Create with eventScenario, airportHeliport, airspace",
            "Response contains same filter values",
            TestStatus.ROADMAP
        ));

        scenarios.add(new TestScenario(
            "DM-03",
            "Optional fields persistence",
            CATEGORY_DATA_MODEL,
            "Validate all optional fields returned",
            "Create with eventSeries, publisher, comment, description",
            "All optional fields echoed in response",
            TestStatus.ROADMAP
        ));

        scenarios.add(new TestScenario(
            "LC-01",
            "Complete lifecycle",
            "Lifecycle",
            "Test full subscription lifecycle",
            "Create → Activate → Pause → Delete",
            "All state transitions succeed; final GET returns 404",
            TestStatus.ROADMAP
        ));

        scenarios.add(new TestScenario(
            "RS-01",
            "Concurrent creation",
            "Resilience",
            "Test concurrent subscription creation",
            "Send 50 POST requests in parallel",
            "All succeed OR some conflict (409); no 500 errors; no duplicate IDs",
            TestStatus.ROADMAP
        ));
    }

    public List<TestScenario> getAllScenarios() {
        return new ArrayList<>(scenarios);
    }

    public List<TestScenario> getScenariosByCategory(String category) {
        return scenarios.stream()
            .filter(s -> s.category().equalsIgnoreCase(category))
            .toList();
    }

    public List<TestScenario> getImplementedScenarios() {
        return scenarios.stream()
            .filter(s -> s.implementationStatus() == TestStatus.IMPLEMENTED)
            .toList();
    }

    public Optional<TestScenario> getScenario(String id) {
        return scenarios.stream()
            .filter(s -> s.id().equals(id))
            .findFirst();
    }
}
