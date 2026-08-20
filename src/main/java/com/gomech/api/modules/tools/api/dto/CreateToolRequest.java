package com.gomech.api.modules.tools.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record CreateToolRequest(
        @NotNull UUID unitId,
        UUID categoryId,
        @NotBlank String assetTag,
        String serialNumber,
        @NotBlank String name,
        String brand,
        String model,
        String locationInUnit,
        LocalDate purchaseDate,
        BigDecimal purchaseCost,
        Integer initialMaintenanceIntervalDays
) {}
