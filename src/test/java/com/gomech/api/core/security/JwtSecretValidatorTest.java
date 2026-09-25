package com.gomech.api.core.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretValidatorTest {

    private static final String STRONG_SECRET =
        "9f1c2b7a4d6e8f0a1b3c5d7e9f2a4b6c8d0e1f3a5b7c9d2e4f6a8b0c1d3e5f7a";
    private static final String PUBLIC_SECRET =
        "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Test
    @DisplayName("Rejects a missing secret outside and inside the local profile")
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> JwtSecretValidator.validate(null, false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> JwtSecretValidator.validate("  ", true))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Rejects a secret shorter than 256 bits")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> JwtSecretValidator.validate("c2VjcmV0", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("curto");
    }

    @Test
    @DisplayName("Rejects a secret that is not Base64")
    void rejectsNonBase64Secret() {
        assertThatThrownBy(() -> JwtSecretValidator.validate("not base64 at all!!".repeat(4), false))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Rejects publicly known secrets outside the local profile, accepts them locally")
    void publicSecretsAreLocalOnly() {
        assertThatThrownBy(() -> JwtSecretValidator.validate(PUBLIC_SECRET, false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("local");
        assertThatCode(() -> JwtSecretValidator.validate(PUBLIC_SECRET, true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Accepts a strong, unique secret in any profile")
    void acceptsStrongSecret() {
        assertThatCode(() -> JwtSecretValidator.validate(STRONG_SECRET, false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Fails at construction time so a misconfigured deployment does not boot")
    void failsWhenConstructedWithoutLocalProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new JwtSecretValidator("", prod)).isInstanceOf(IllegalStateException.class);

        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        assertThatCode(() -> new JwtSecretValidator(PUBLIC_SECRET, local)).doesNotThrowAnyException();
    }
}
