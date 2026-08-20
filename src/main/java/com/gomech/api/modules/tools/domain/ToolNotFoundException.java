package com.gomech.api.modules.tools.domain;

import java.util.UUID;

public class ToolNotFoundException extends RuntimeException {
    public ToolNotFoundException(UUID id) {
        super("Tool not found with ID: " + id);
    }

    public ToolNotFoundException(String message) {
        super(message);
    }
}
