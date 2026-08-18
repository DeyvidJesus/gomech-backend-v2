package com.gomech.api.core.authorization.api;

import java.util.Map;

public record AuthorizationRequest(
    String action,
    String resource,
    String resourceId,
    Map<String, String> attributes
) {
}
