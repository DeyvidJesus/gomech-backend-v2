package com.gomech.api.modules.iam;

import com.gomech.api.core.security.JwtUtil;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.LoginRequest;
import com.gomech.api.modules.iam.api.dto.RefreshTokenRequest;
import com.gomech.api.modules.iam.application.AuthService;
import com.gomech.api.modules.iam.domain.UserStatus;
import com.gomech.api.modules.iam.infrastructure.persistence.model.*;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UnitRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private Unit testUnit;
    private Role testRole;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtExpiration", 900_000L);
        ReflectionTestUtils.setField(authService, "jwtRefreshExpiration", 604_800_000L);

        UUID tenantId = UUID.randomUUID();

        testUnit = new Unit();
        testUnit.setId(UUID.randomUUID());
        testUnit.setTenantId(tenantId);
        testUnit.setName("Matriz");

        testRole = new Role();
        testRole.setId(UUID.randomUUID());
        testRole.setName("Proprietário");

        Permission perm = new Permission();
        perm.setId(UUID.randomUUID());
        perm.setCode("iam:read");
        perm.setModule("IAM");
        testRole.setPermissions(Set.of(perm));

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setTenantId(tenantId);
        testUser.setName("João da Silva");
        testUser.setEmail("joao@oficina.com.br");
        testUser.setPasswordHash("$2a$10$hashedPassword");
        testUser.setStatus(UserStatus.ACTIVE);

        UserRole userRole = new UserRole();
        userRole.setUser(testUser);
        userRole.setRole(testRole);
        userRole.setUnit(testUnit);
        userRole.setTenantId(tenantId);
        testUser.getUserRoles().add(userRole);
    }

    @Test
    @DisplayName("Should successfully authenticate user with valid credentials")
    void shouldLoginSuccessfully() {
        when(userRepository.findByEmail("joao@oficina.com.br")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("secret123", testUser.getPasswordHash())).thenReturn(true);
        when(jwtUtil.generateToken(any(), any(), any(), any(), any())).thenReturn("mock-access-token");
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginRequest request = new LoginRequest("joao@oficina.com.br", "secret123");
        AuthResponse response = authService.login(request, "127.0.0.1", "JUnit", "Desktop");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("mock-access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user()).isNotNull();
        assertThat(response.user().email()).isEqualTo("joao@oficina.com.br");
        verify(userSessionRepository).save(any(UserSession.class));
    }

    @Test
    @DisplayName("Should reject login when password does not match")
    void shouldRejectInvalidPassword() {
        when(userRepository.findByEmail("joao@oficina.com.br")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", testUser.getPasswordHash())).thenReturn(false);

        LoginRequest request = new LoginRequest("joao@oficina.com.br", "wrongpassword");

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "JUnit", "Desktop"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Credenciais inválidas");

        verify(userSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject login when user is suspended or inactive")
    void shouldRejectInactiveUser() {
        testUser.setStatus("SUSPENDED");
        when(userRepository.findByEmail("joao@oficina.com.br")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("secret123", testUser.getPasswordHash())).thenReturn(true);

        LoginRequest request = new LoginRequest("joao@oficina.com.br", "secret123");

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "JUnit", "Desktop"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usuário inativo ou suspenso");
    }

    @Test
    @DisplayName("Should rotate refresh token and issue new access token")
    void shouldRotateRefreshTokenSuccessfully() {
        UUID familyId = UUID.randomUUID();
        UserSession activeSession = new UserSession();
        activeSession.setId(UUID.randomUUID());
        activeSession.setUser(testUser);
        activeSession.setTenantId(testUser.getTenantId());
        activeSession.setFamilyId(familyId);
        activeSession.setRefreshToken("old-refresh-token");
        activeSession.setExpiresAt(OffsetDateTime.now().plusDays(7));
        activeSession.setRevoked(false);

        when(userSessionRepository.findByRefreshToken("old-refresh-token")).thenReturn(Optional.of(activeSession));
        when(jwtUtil.generateToken(any(), any(), any(), any(), any())).thenReturn("new-jwt-token");
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenRequest request = new RefreshTokenRequest("old-refresh-token");
        AuthResponse response = authService.refreshToken(request, "127.0.0.1", "JUnit", "Desktop");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new-jwt-token");
        assertThat(response.refreshToken()).isNotEqualTo("old-refresh-token");
        assertThat(activeSession.isRevoked()).isTrue();
        assertThat(activeSession.getReplacedBy()).isNotNull();
        verify(userSessionRepository, times(2)).save(any(UserSession.class));
    }

    @Test
    @DisplayName("Reuse Detection: Presenting revoked refresh token must revoke entire family")
    void shouldTriggerReuseDetectionWhenRevokedTokenIsPresented() {
        UUID familyId = UUID.randomUUID();
        UserSession alreadyRevokedSession = new UserSession();
        alreadyRevokedSession.setId(UUID.randomUUID());
        alreadyRevokedSession.setUser(testUser);
        alreadyRevokedSession.setFamilyId(familyId);
        alreadyRevokedSession.setRefreshToken("stolen-revoked-token");
        alreadyRevokedSession.setExpiresAt(OffsetDateTime.now().plusDays(7));
        alreadyRevokedSession.setRevoked(true);
        alreadyRevokedSession.setRevokedAt(OffsetDateTime.now().minusHours(1));

        when(userSessionRepository.findByRefreshToken("stolen-revoked-token")).thenReturn(Optional.of(alreadyRevokedSession));

        RefreshTokenRequest request = new RefreshTokenRequest("stolen-revoked-token");

        assertThatThrownBy(() -> authService.refreshToken(request, "192.168.1.100", "AttackerAgent", "Unknown"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Token de atualização revogado ou reutilizado ilegalmente");

        // Verify that all sessions in that family were revoked immediately
        verify(userSessionRepository).revokeAllByFamilyId(eq(familyId), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Should switch unit and generate token with target unit scope")
    void shouldSwitchUnitSuccessfully() {
        when(userRepository.findByIdWithRoles(testUser.getId())).thenReturn(Optional.of(testUser));
        when(unitRepository.findById(testUnit.getId())).thenReturn(Optional.of(testUnit));
        when(jwtUtil.generateToken(eq(testUser.getId()), eq(testUser.getTenantId()), eq(testUnit.getId()), any(), any()))
                .thenReturn("unit-scoped-jwt");

        AuthResponse response = authService.switchUnit(testUser.getId(), testUnit.getId());

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("unit-scoped-jwt");
        assertThat(response.user().activeUnitId()).isEqualTo(testUnit.getId());
    }

    @Test
    @DisplayName("Should reject unit switch if user has no authorization in target unit")
    void shouldRejectUnauthorizedUnitSwitch() {
        Role mechanicRole = new Role();
        mechanicRole.setId(UUID.randomUUID());
        mechanicRole.setName("Mecânico");

        User mechanicUser = new User();
        mechanicUser.setId(UUID.randomUUID());
        mechanicUser.setTenantId(testUser.getTenantId());
        mechanicUser.setStatus(UserStatus.ACTIVE);

        UserRole ur = new UserRole();
        ur.setUser(mechanicUser);
        ur.setRole(mechanicRole);
        ur.setUnit(testUnit);
        mechanicUser.getUserRoles().add(ur);

        when(userRepository.findByIdWithRoles(mechanicUser.getId())).thenReturn(Optional.of(mechanicUser));

        UUID unauthorizedUnitId = UUID.randomUUID();
        Unit unauthorizedUnit = new Unit();
        unauthorizedUnit.setId(unauthorizedUnitId);
        unauthorizedUnit.setTenantId(mechanicUser.getTenantId());
        when(unitRepository.findById(unauthorizedUnitId)).thenReturn(Optional.of(unauthorizedUnit));

        assertThatThrownBy(() -> authService.switchUnit(mechanicUser.getId(), unauthorizedUnitId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não possui acesso");
    }

    @Test
    @DisplayName("Should reject unit switch if target unit belongs to another tenant")
    void shouldRejectUnitSwitchToAnotherTenant() {
        when(userRepository.findByIdWithRoles(testUser.getId())).thenReturn(Optional.of(testUser));

        UUID otherTenantUnitId = UUID.randomUUID();
        Unit otherTenantUnit = new Unit();
        otherTenantUnit.setId(otherTenantUnitId);
        otherTenantUnit.setTenantId(UUID.randomUUID());
        when(unitRepository.findById(otherTenantUnitId)).thenReturn(Optional.of(otherTenantUnit));

        assertThatThrownBy(() -> authService.switchUnit(testUser.getId(), otherTenantUnitId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pertence a outra organização");
    }

    @Test
    @DisplayName("Should revoke single session on logout")
    void shouldRevokeSessionOnLogout() {
        UserSession session = new UserSession();
        session.setRefreshToken("valid-token");
        session.setRevoked(false);

        when(userSessionRepository.findByRefreshToken("valid-token")).thenReturn(Optional.of(session));

        authService.logout("valid-token");

        assertThat(session.isRevoked()).isTrue();
        verify(userSessionRepository).save(session);
    }
}
