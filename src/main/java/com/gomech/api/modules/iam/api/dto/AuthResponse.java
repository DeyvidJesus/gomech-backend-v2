package com.gomech.api.modules.iam.api.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}
