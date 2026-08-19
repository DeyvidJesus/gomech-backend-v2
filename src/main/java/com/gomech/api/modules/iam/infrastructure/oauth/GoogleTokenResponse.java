package com.gomech.api.modules.iam.infrastructure.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("scope") String scope,
        @JsonProperty("id_token") String idToken,
        @JsonProperty("refresh_token") String refreshToken
) {}
