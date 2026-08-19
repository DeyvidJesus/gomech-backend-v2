package com.gomech.api.modules.billing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Status consolidado de capacidades, cotas e limites do Tenant")
public record EntitlementStatusResponse(
        @Schema(description = "ID do Tenant")
        UUID tenantId,

        @Schema(description = "Plano contratado", example = "PRO")
        String planCode,

        @Schema(description = "Status da assinatura", example = "ACTIVE")
        String subscriptionStatus,

        @Schema(description = "Módulos liberados para uso")
        Set<String> enabledModules,

        @Schema(description = "Limites de cota vigentes")
        Map<String, Long> quotaLimits,

        @Schema(description = "Consumo atual no ciclo de faturamento")
        Map<String, Long> currentUsage
) {}
