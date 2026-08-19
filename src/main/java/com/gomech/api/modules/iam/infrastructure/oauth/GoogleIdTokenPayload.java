package com.gomech.api.modules.iam.infrastructure.oauth;

public record GoogleIdTokenPayload(
        String sub,
        String email,
        boolean emailVerified,
        String name,
        String picture,
        String nonce
) {}
