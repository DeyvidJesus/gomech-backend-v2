package com.gomech.api.modules.crm.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Dados para cadastro de um novo veículo no CRM")
public record CreateVehicleRequest(
        @NotNull(message = "O cliente proprietário é obrigatório")
        @Schema(description = "Identificador único do cliente proprietário")
        UUID customerId,

        @NotBlank(message = "A placa do veículo é obrigatória")
        @Size(max = 20, message = "A placa não pode exceder 20 caracteres")
        @Schema(description = "Placa do veículo (Mercosul ou Tradicional)", example = "ABC1D23")
        String licensePlate,

        @Size(max = 100, message = "A marca não pode exceder 100 caracteres")
        @Schema(description = "Marca / Fabricante", example = "Toyota")
        String brand,

        @Size(max = 100, message = "O modelo não pode exceder 100 caracteres")
        @Schema(description = "Modelo do veículo", example = "Corolla XEi 2.0")
        String model,

        @Schema(description = "Ano de fabricação / modelo", example = "2022")
        Integer year,

        @Size(max = 50, message = "O Chassi (VIN) não pode exceder 50 caracteres")
        @Schema(description = "Número do Chassi (VIN)", example = "9BRBL42E1M0123456")
        String vin,

        @Schema(description = "Quilometragem atual do veículo", example = "35000")
        Integer currentMileage
) {
}
