package com.gomech.api.modules.iam.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID familyId,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime expiresAt,
        String ipAddress,
        String userAgent,
        String deviceInfo,
        boolean isCurrent
) {}
