package com.gomech.api.modules.finance.api.dto;

import com.gomech.api.modules.finance.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class AccountDtos {

    @Builder
    public record Create(
            @NotNull UUID unitId,
            @NotBlank String name,
            @NotNull AccountType type,
            String bankName,
            String accountNumber,
            String agency,
            BigDecimal initialBalance
    ) {}

    @Builder
    public record Update(
            @NotBlank String name,
            String bankName,
            String accountNumber,
            String agency,
            Boolean isActive
    ) {}

    @Builder
    public record Response(
            UUID id,
            UUID tenantId,
            UUID unitId,
            String name,
            AccountType type,
            String bankName,
            String accountNumber,
            String agency,
            BigDecimal initialBalance,
            BigDecimal currentBalance,
            Boolean isActive,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
