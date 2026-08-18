package com.gomech.api.modules.iam.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RegisterWorkshopRequest(
        @NotBlank String workshopName,
        @NotBlank String address,
        @NotNull Integer bays,
        List<String> services,
        
        @NotBlank String ownerName,
        @NotBlank @Email String email,
        @NotBlank String password
) {}
