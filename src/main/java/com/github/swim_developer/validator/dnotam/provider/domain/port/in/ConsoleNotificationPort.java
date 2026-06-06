package com.github.swim_developer.validator.dnotam.provider.domain.port.in;

import com.github.swim_developer.validator.dnotam.provider.domain.model.MessageData;

public interface ConsoleNotificationPort {
    void messageReceived(String userId, MessageData messageData);
    void info(String userId, String message);
    void success(String userId, String message);
    void warning(String userId, String message);
    void error(String userId, String message);
}
