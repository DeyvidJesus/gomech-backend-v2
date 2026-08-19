package com.gomech.api.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    // 256-bit base64 secret key for testing
    private final String testSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long testExpirationMs = 900_000L; // 15 min

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", testSecret);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", testExpirationMs);
    }

    @Test
    @DisplayName("Should generate valid JWT with user ID, tenant ID, unit ID, roles, and permissions")
    void shouldGenerateValidTokenWithAllClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        List<String> roles = List.of("ROLE_OWNER", "ROLE_ADMIN");
        List<String> permissions = List.of("iam:read", "workorders:write");

        String token = jwtUtil.generateToken(userId, tenantId, unitId, roles, permissions);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(userId.toString());
        assertThat(jwtUtil.extractTenantId(token)).isEqualTo(tenantId);
        assertThat(jwtUtil.extractUnitId(token)).isEqualTo(unitId);
        assertThat(jwtUtil.extractRoles(token)).containsExactlyInAnyOrder("ROLE_OWNER", "ROLE_ADMIN");
        assertThat(jwtUtil.extractPermissions(token)).containsExactlyInAnyOrder("iam:read", "workorders:write");
        assertThat(jwtUtil.extractTokenId(token)).isNotBlank();
        assertThat(jwtUtil.extractIssuedAt(token)).isBeforeOrEqualTo(new Date());
        assertThat(jwtUtil.extractExpiration(token)).isAfter(new Date());
        assertTrue(jwtUtil.isTokenValid(token, userId));
        assertFalse(jwtUtil.isTokenValid(token, UUID.randomUUID()));
    }

    @Test
    @DisplayName("Should support two-argument generateToken for backwards compatibility")
    void shouldSupportTwoArgumentGenerateToken() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = jwtUtil.generateToken(userId, tenantId);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(userId.toString());
        assertThat(jwtUtil.extractTenantId(token)).isEqualTo(tenantId);
        assertThat(jwtUtil.extractUnitId(token)).isNull();
        assertThat(jwtUtil.extractRoles(token)).isEmpty();
        assertThat(jwtUtil.extractPermissions(token)).isEmpty();
        assertThat(jwtUtil.extractTokenId(token)).isNotBlank();
    }
}
