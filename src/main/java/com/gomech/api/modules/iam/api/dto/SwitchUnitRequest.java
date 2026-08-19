package com.gomech.api.modules.iam.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SwitchUnitRequest(
        @NotNull UUID unitId
) {}
