package com.gomech.api.modules.iam.api.dto;

import java.util.List;
import java.util.UUID;

public record UserSummaryDto(
        UUID id,
        String name,
        String email,
        UUID tenantId,
        UUID activeUnitId,
        List<String> roles,
        List<String> permissions
) {}
