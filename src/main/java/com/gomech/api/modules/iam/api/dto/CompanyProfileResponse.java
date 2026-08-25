package com.gomech.api.modules.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Dados do perfil cadastral da empresa e matriz")
public record CompanyProfileResponse(
        UUID id,
        String name,
        String tradeName,
        String cnpj,
        String email,
        String phone,
        String logoUrl,
        String address,
        String status
) {
}
