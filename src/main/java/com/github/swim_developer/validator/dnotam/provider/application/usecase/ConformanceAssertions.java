package com.github.swim_developer.validator.dnotam.provider.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.swim_developer.validator.dnotam.provider.domain.model.HttpResult;
import com.github.swim_developer.validator.provider.domain.model.AssertionResult;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConformanceAssertions {

    private static final String ASSERT_FIELD_PREFIX = "Field '";

    public AssertionResult assertStatusCode(HttpResult response, int expected) {
        int actual = response.statusCode();
        return new AssertionResult(
                "HTTP status code",
                actual == expected,
                String.valueOf(expected),
                String.valueOf(actual),
                actual == expected
                        ? "Status code matches"
                        : "Expected " + expected + " but got " + actual
        );
    }

    public AssertionResult assertFieldPresent(JsonNode json, String fieldName) {
        boolean present = json.has(fieldName) && !json.get(fieldName).isNull();
        return new AssertionResult(
                ASSERT_FIELD_PREFIX + fieldName + "' present",
                present,
                "Field present",
                present ? "Present" : "Missing",
                present ? "Field found" : "Field missing: " + fieldName
        );
    }

    public AssertionResult assertArrayFieldPresent(JsonNode json, String fieldName) {
        boolean present = json.has(fieldName) && json.get(fieldName).isArray();
        return new AssertionResult(
                "Array field '" + fieldName + "' present",
                present,
                "Array field present",
                present ? "Present" : "Missing",
                present ? "Array field found" : "Array field missing: " + fieldName
        );
    }

    public AssertionResult assertFieldEquals(JsonNode json, String fieldName, Object expected) {
        if (!json.has(fieldName)) {
            return new AssertionResult(
                    ASSERT_FIELD_PREFIX + fieldName + "' equals " + expected,
                    false,
                    String.valueOf(expected),
                    "Field missing",
                    "Field not found: " + fieldName
            );
        }

        JsonNode fieldNode = json.get(fieldName);
        String actual;
        boolean equals;

        if (expected instanceof Boolean bool) {
            actual = String.valueOf(fieldNode.asBoolean());
            equals = fieldNode.asBoolean() == bool;
        } else {
            actual = fieldNode.asText();
            equals = actual.equals(String.valueOf(expected));
        }

        return new AssertionResult(
                ASSERT_FIELD_PREFIX + fieldName + "' equals " + expected,
                equals,
                String.valueOf(expected),
                actual,
                equals ? "Values match" : "Expected '" + expected + "' but got '" + actual + "'"
        );
    }
}
