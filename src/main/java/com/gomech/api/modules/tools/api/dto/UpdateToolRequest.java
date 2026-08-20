package com.gomech.api.modules.tools.api.dto;

import com.gomech.api.modules.tools.domain.ToolStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record UpdateToolRequest(
        UUID categoryId,
        @NotBlank String assetTag,
        String serialNumber,
        @NotBlank String name,
        String brand,
        String model,
        ToolStatus status,
        String locationInUnit,
        LocalDate purchaseDate,
        BigDecimal purchaseCost
) {}
