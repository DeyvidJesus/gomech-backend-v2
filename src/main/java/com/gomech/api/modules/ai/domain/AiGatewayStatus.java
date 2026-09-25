package com.gomech.api.modules.ai.domain;

public enum AiGatewayStatus {
    SUCCESS,
    FAILED,
    REJECTED_UNAUTHORIZED,
    REJECTED_QUOTA_EXCEEDED,
    RATE_LIMITED,
    TIMEOUT
}
