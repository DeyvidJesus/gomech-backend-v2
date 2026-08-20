package com.gomech.api.modules.tools.domain;

import java.util.UUID;

public class ToolUnavailableException extends RuntimeException {
    public ToolUnavailableException(UUID toolId, ToolStatus status) {
        super(String.format("Tool %s is not available for this operation. Current status: %s", toolId, status));
    }

    public ToolUnavailableException(String message) {
        super(message);
    }
}
