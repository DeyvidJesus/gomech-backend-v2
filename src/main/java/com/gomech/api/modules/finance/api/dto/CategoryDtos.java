package com.gomech.api.modules.finance.api.dto;

import com.gomech.api.modules.finance.domain.DreCategoryType;
import com.gomech.api.modules.finance.domain.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

public class CategoryDtos {

    @Builder
    public record Create(
            @NotBlank String name,
            @NotNull TransactionType type,
            @NotNull DreCategoryType dreCategoryType
    ) {}

    @Builder
    public record Update(
            @NotBlank String name,
            @NotNull TransactionType type,
            @NotNull DreCategoryType dreCategoryType,
            Boolean isActive
    ) {}

    @Builder
    public record Response(
            UUID id,
            UUID tenantId,
            String name,
            TransactionType type,
            DreCategoryType dreCategoryType,
            Boolean isActive,
            Instant createdAt
    ) {}
}
