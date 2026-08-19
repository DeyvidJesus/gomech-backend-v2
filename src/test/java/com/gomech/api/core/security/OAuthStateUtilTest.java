package com.gomech.api.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthStateUtilTest {

    private OAuthStateUtil oAuthStateUtil;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        oAuthStateUtil = new OAuthStateUtil(secret);
    }

    @Test
    @DisplayName("Should generate valid OAuth context with signed state, nonce, and PKCE challenge")
    void shouldGenerateValidContext() {
        String redirectUri = "http://localhost:3000/callback";
        OAuthStateUtil.GeneratedOAuthContext context = oAuthStateUtil.generateContext(redirectUri);

        assertThat(context.signedState()).isNotBlank();
        assertThat(context.nonce()).isNotBlank();
        assertThat(context.codeVerifier()).isNotBlank();
        assertThat(context.codeChallenge()).isNotBlank();

        // Validar que o estado assinado pode ser decodificado e verificado
        OAuthStateUtil.OAuthStateData stateData = oAuthStateUtil.validateAndExtractState(context.signedState());
        assertThat(stateData.nonce()).isEqualTo(context.nonce());
        assertThat(stateData.codeVerifier()).isEqualTo(context.codeVerifier());
        assertThat(stateData.redirectUri()).isEqualTo(redirectUri);
    }

    @Test
    @DisplayName("Should reject tampered signed state token")
    void shouldRejectTamperedState() {
        OAuthStateUtil.GeneratedOAuthContext context = oAuthStateUtil.generateContext("http://localhost:3000/callback");
        String tamperedState = context.signedState() + "tampered";

        assertThatThrownBy(() -> oAuthStateUtil.validateAndExtractState(tamperedState))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Should reject state signed with a different key")
    void shouldRejectStateWithDifferentKey() {
        OAuthStateUtil otherUtil = new OAuthStateUtil("0123456789012345678901234567890123456789012345678901234567890123");
        OAuthStateUtil.GeneratedOAuthContext context = otherUtil.generateContext("http://localhost:3000/callback");

        assertThatThrownBy(() -> oAuthStateUtil.validateAndExtractState(context.signedState()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Should generate deterministic PKCE challenge for given verifier")
    void shouldGenerateConsistentChallenge() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = oAuthStateUtil.generateCodeChallenge(verifier);

        assertThat(challenge).isNotBlank();
        assertThat(oAuthStateUtil.generateCodeChallenge(verifier)).isEqualTo(challenge);
    }
}
