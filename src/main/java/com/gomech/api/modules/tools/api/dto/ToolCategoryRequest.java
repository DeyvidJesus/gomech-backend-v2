package com.gomech.api.modules.tools.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

public class ToolCategoryRequest {

    @Builder
    public record Create(
            @NotBlank String name,
            String description,
            Boolean requiresCalibration,
            Integer defaultMaintenanceIntervalDays
    ) {}

    @Builder
    public record Update(
            @NotBlank String name,
            String description,
            Boolean requiresCalibration,
            Integer defaultMaintenanceIntervalDays
    ) {}
}
