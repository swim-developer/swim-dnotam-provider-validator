package com.github.swim_developer.validator.dnotam.provider.domain.port.in;

public interface MapQueryPort {
    String generateMapWithAllEvents();
    String generateMapWithRecentEvents(int limit);
    String generateMapWithRecentMinutes(int minutes);
}
