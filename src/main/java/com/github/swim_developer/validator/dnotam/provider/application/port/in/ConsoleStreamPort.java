package com.github.swim_developer.validator.dnotam.provider.application.port.in;

import com.github.swim_developer.validator.provider.domain.model.ConsoleEntry;
import io.smallrye.mutiny.Multi;

public interface ConsoleStreamPort {
    Multi<ConsoleEntry> streamForUser(String userId);
}
