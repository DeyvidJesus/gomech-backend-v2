package com.gomech.api.core.audit.api;

import java.util.Map;

public record AuditRecordRequest(
    String action,
    String resource,
    String resourceId,
    Map<String, String> metadata
) {
}
