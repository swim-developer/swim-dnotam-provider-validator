package com.github.swim_developer.validator.dnotam.provider.infrastructure.config;

import com.github.swim_developer.validator.dnotam.provider.domain.model.DnotamEvent;
import com.github.swim_developer.validator.dnotam.provider.domain.model.MessageData;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {DnotamEvent.class, MessageData.class})
public class ReflectionConfiguration {
}
