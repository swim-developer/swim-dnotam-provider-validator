package com.github.swim_developer.validator.dnotam.provider.infrastructure.messaging;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConnectionTrackerPort;

@Slf4j
@ApplicationScoped
public class AmqpConnectionCleanupScheduler {

    private final ConnectionTrackerPort userConnectionTracker;

    @Inject
    public AmqpConnectionCleanupScheduler(ConnectionTrackerPort userConnectionTracker) {
        this.userConnectionTracker = userConnectionTracker;
    }

    @Scheduled(every = "30s")
    void cleanup() {
        userConnectionTracker.performCleanup();
    }
}
