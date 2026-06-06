package com.github.swim_developer.validator.dnotam.provider.domain.port.in;

import com.github.swim_developer.validator.provider.domain.model.TestResult;
import com.github.swim_developer.validator.provider.domain.model.TestScenario;

public interface ConformanceTestPort {
    TestResult executeScenario(TestScenario scenario, String providerUrl,
            String authToken, String userId);
}
