package com.gomech.api.modules.crm.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Detalhes completos do veículo no CRM")
public record VehicleResponse(
        @Schema(description = "Identificador único do veículo")
        UUID id,

        @Schema(description = "Identificador do Tenant")
        UUID tenantId,

        @Schema(description = "Identificador do cliente proprietário")
        UUID customerId,

        @Schema(description = "Nome do cliente proprietário")
        String customerName,

        @Schema(description = "Placa do veículo normalizada", example = "ABC1D23")
        String licensePlate,

        @Schema(description = "Placa do veículo formatada", example = "ABC1D23")
        String formattedLicensePlate,

        @Schema(description = "Marca / Fabricante", example = "Toyota")
        String brand,

        @Schema(description = "Modelo do veículo", example = "Corolla XEi 2.0")
        String model,

        @Schema(description = "Ano de fabricação / modelo", example = "2022")
        Integer year,

        @Schema(description = "Número do Chassi (VIN)", example = "9BRBL42E1M0123456")
        String vin,

        @Schema(description = "Quilometragem atual", example = "35000")
        Integer currentMileage,

        @Schema(description = "Data de cadastro")
        OffsetDateTime createdAt,

        @Schema(description = "Data da última atualização")
        OffsetDateTime updatedAt
) {
}
