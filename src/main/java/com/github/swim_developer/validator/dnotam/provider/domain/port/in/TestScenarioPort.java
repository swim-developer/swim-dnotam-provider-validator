package com.github.swim_developer.validator.dnotam.provider.domain.port.in;

import com.github.swim_developer.validator.provider.domain.model.TestScenario;

import java.util.List;
import java.util.Optional;

public interface TestScenarioPort {
    List<TestScenario> getAllScenarios();
    List<TestScenario> getScenariosByCategory(String category);
    List<TestScenario> getImplementedScenarios();
    Optional<TestScenario> getScenario(String id);
}
