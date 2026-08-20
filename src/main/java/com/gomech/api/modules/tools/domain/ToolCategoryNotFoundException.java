package com.gomech.api.modules.tools.domain;

import java.util.UUID;

public class ToolCategoryNotFoundException extends RuntimeException {
    public ToolCategoryNotFoundException(UUID id) {
        super("Tool category not found with ID: " + id);
    }
}
