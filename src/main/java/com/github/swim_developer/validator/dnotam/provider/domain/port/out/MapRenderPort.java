package com.github.swim_developer.validator.dnotam.provider.domain.port.out;

import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;

import java.util.List;

public interface MapRenderPort {
    String generateMapWithEvents(List<ReceivedMessage> events);
}
