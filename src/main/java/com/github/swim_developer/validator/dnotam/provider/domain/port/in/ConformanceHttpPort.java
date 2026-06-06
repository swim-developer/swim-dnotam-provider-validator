package com.github.swim_developer.validator.dnotam.provider.domain.port.in;

import com.github.swim_developer.validator.dnotam.provider.domain.model.HttpResult;

public interface ConformanceHttpPort {
    HttpResult executePost(String url, String authToken, String requestBody);
    HttpResult executeGet(String url, String authToken);
}
