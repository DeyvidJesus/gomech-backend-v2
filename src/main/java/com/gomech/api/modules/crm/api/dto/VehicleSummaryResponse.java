package com.gomech.api.modules.crm.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Resumo do veículo para projeção em listagens e associações")
public record VehicleSummaryResponse(
        @Schema(description = "Identificador único do veículo")
        UUID id,

        @Schema(description = "Identificador do cliente proprietário")
        UUID customerId,

        @Schema(description = "Nome do cliente proprietário")
        String customerName,

        @Schema(description = "Placa veicular normalizada", example = "ABC1D23")
        String licensePlate,

        @Schema(description = "Placa veicular formatada", example = "ABC1D23")
        String formattedLicensePlate,

        @Schema(description = "Marca / Fabricante", example = "Volkswagen")
        String brand,

        @Schema(description = "Modelo", example = "Gol 1.6 MSI")
        String model,

        @Schema(description = "Ano de fabricação / modelo", example = "2021")
        Integer year,

        @Schema(description = "Quilometragem atual registrada", example = "45000")
        Integer currentMileage
) {
}
