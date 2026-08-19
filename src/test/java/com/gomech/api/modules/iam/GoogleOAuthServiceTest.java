package com.gomech.api.modules.iam;

import com.gomech.api.core.security.JwtUtil;
import com.gomech.api.core.security.OAuthStateUtil;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.GoogleAuthorizeUrlResponse;
import com.gomech.api.modules.iam.api.dto.GoogleOAuthCallbackRequest;
import com.gomech.api.modules.iam.application.GoogleOAuthClient;
import com.gomech.api.modules.iam.application.GoogleOAuthService;
import com.gomech.api.modules.iam.domain.UserStatus;
import com.gomech.api.modules.iam.infrastructure.oauth.GoogleIdTokenPayload;
import com.gomech.api.modules.iam.infrastructure.oauth.GoogleTokenResponse;
import com.gomech.api.modules.iam.infrastructure.persistence.model.*;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {

    @Mock private GoogleOAuthClient googleOAuthClient;
    @Mock private OAuthStateUtil oAuthStateUtil;
    @Mock private UserRepository userRepository;
    @Mock private UserIdentityRepository userIdentityRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UnitRepository unitRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    private GoogleOAuthService googleOAuthService;

    @BeforeEach
    void setUp() {
        googleOAuthService = new GoogleOAuthService(
                googleOAuthClient,
                oAuthStateUtil,
                userRepository,
                userIdentityRepository,
                tenantRepository,
                unitRepository,
                roleRepository,
                userSessionRepository,
                passwordEncoder,
                jwtUtil
        );

        ReflectionTestUtils.setField(googleOAuthService, "clientId", "test-client-id");
        ReflectionTestUtils.setField(googleOAuthService, "defaultRedirectUri", "http://localhost:3000/callback");
        ReflectionTestUtils.setField(googleOAuthService, "authorizationUri", "https://accounts.google.com/o/oauth2/v2/auth");
        ReflectionTestUtils.setField(googleOAuthService, "jwtExpiration", 900000L);
        ReflectionTestUtils.setField(googleOAuthService, "jwtRefreshExpiration", 604800000L);
    }

    @Test
    @DisplayName("Should generate valid Google authorize URL with state and PKCE parameters")
    void shouldGenerateAuthorizeUrl() {
        OAuthStateUtil.GeneratedOAuthContext mockContext = new OAuthStateUtil.GeneratedOAuthContext(
                "signed-state-token",
                "nonce-123",
                "verifier-abc",
                "challenge-xyz"
        );
        when(oAuthStateUtil.generateContext("http://localhost:3000/callback")).thenReturn(mockContext);

        GoogleAuthorizeUrlResponse response = googleOAuthService.generateAuthorizeUrl(null);

        assertThat(response.state()).isEqualTo("signed-state-token");
        assertThat(response.authorizationUrl())
                .contains("client_id=test-client-id")
                .contains("state=signed-state-token")
                .contains("nonce=nonce-123")
                .contains("code_challenge=challenge-xyz")
                .contains("code_challenge_method=S256");
    }

    @Test
    @DisplayName("Should authenticate existing linked identity and issue GoMech tokens")
    void shouldAuthenticateExistingLinkedIdentity() {
        String state = "valid-signed-state";
        String code = "auth-code-123";
        String nonce = "nonce-456";
        String sub = "google-sub-789";
        String email = "mario@oficina.com.br";

        OAuthStateUtil.OAuthStateData stateData = new OAuthStateUtil.OAuthStateData("state-id", nonce, "verifier", "http://localhost:3000/callback");
        when(oAuthStateUtil.validateAndExtractState(state)).thenReturn(stateData);

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse("access-token", 3600L, "Bearer", "openid email", "id-token-jwt", null);
        when(googleOAuthClient.exchangeCode(eq(code), eq("verifier"), eq("http://localhost:3000/callback"))).thenReturn(tokenResponse);

        GoogleIdTokenPayload idTokenPayload = new GoogleIdTokenPayload(sub, email, true, "Mario Silva", null, nonce);
        when(googleOAuthClient.verifyAndExtractIdToken("id-token-jwt", nonce)).thenReturn(idTokenPayload);

        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setName("Mario Silva");
        user.setEmail(email);
        user.setTenantId(tenantId);
        user.setStatus(UserStatus.ACTIVE);

        UserIdentity identity = new UserIdentity(user, tenantId, "GOOGLE", sub, email);
        when(userIdentityRepository.findByProviderAndProviderSubject("GOOGLE", sub)).thenReturn(Optional.of(identity));

        when(jwtUtil.generateToken(any(), any(), any(), any(), any())).thenReturn("gomech-jwt-token");

        AuthResponse response = googleOAuthService.authenticate(
                new GoogleOAuthCallbackRequest(code, state),
                "127.0.0.1", "Chrome", "Desktop"
        );

        assertThat(response.accessToken()).isEqualTo("gomech-jwt-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo(email);
        verify(userSessionRepository, times(1)).save(any(UserSession.class));
    }

    @Test
    @DisplayName("Should link Google identity to existing GoMech user with matching email")
    void shouldLinkGoogleIdentityToMatchingEmailUser() {
        String state = "valid-signed-state";
        String code = "auth-code-123";
        String nonce = "nonce-456";
        String sub = "google-sub-999";
        String email = "pedro@oficina.com.br";

        OAuthStateUtil.OAuthStateData stateData = new OAuthStateUtil.OAuthStateData("state-id", nonce, "verifier", "http://localhost:3000/callback");
        when(oAuthStateUtil.validateAndExtractState(state)).thenReturn(stateData);

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse("access-token", 3600L, "Bearer", "openid email", "id-token-jwt", null);
        when(googleOAuthClient.exchangeCode(eq(code), any(), any())).thenReturn(tokenResponse);

        GoogleIdTokenPayload idTokenPayload = new GoogleIdTokenPayload(sub, email, true, "Pedro Costa", null, nonce);
        when(googleOAuthClient.verifyAndExtractIdToken("id-token-jwt", nonce)).thenReturn(idTokenPayload);

        when(userIdentityRepository.findByProviderAndProviderSubject("GOOGLE", sub)).thenReturn(Optional.empty());

        UUID tenantId = UUID.randomUUID();
        User existingUser = new User();
        existingUser.setName("Pedro Costa");
        existingUser.setEmail(email);
        existingUser.setTenantId(tenantId);
        existingUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

        when(jwtUtil.generateToken(any(), any(), any(), any(), any())).thenReturn("gomech-jwt-token");

        AuthResponse response = googleOAuthService.authenticate(
                new GoogleOAuthCallbackRequest(code, state),
                "127.0.0.1", "Firefox", "Laptop"
        );

        assertThat(response.accessToken()).isEqualTo("gomech-jwt-token");
        ArgumentCaptor<UserIdentity> identityCaptor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).save(identityCaptor.capture());
        assertThat(identityCaptor.getValue().getProviderSubject()).isEqualTo(sub);
        assertThat(identityCaptor.getValue().getEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("Should auto-provision new Tenant, Unit, User and UserIdentity when user does not exist")
    void shouldAutoProvisionNewAccountForNewGoogleUser() {
        String state = "valid-signed-state";
        String code = "auth-code-123";
        String nonce = "nonce-456";
        String sub = "google-sub-new";
        String email = "novo@oficina.com.br";

        OAuthStateUtil.OAuthStateData stateData = new OAuthStateUtil.OAuthStateData("state-id", nonce, "verifier", "http://localhost:3000/callback");
        when(oAuthStateUtil.validateAndExtractState(state)).thenReturn(stateData);

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse("access-token", 3600L, "Bearer", "openid email", "id-token-jwt", null);
        when(googleOAuthClient.exchangeCode(any(), any(), any())).thenReturn(tokenResponse);

        GoogleIdTokenPayload idTokenPayload = new GoogleIdTokenPayload(sub, email, true, "Novo Usuario", null, nonce);
        when(googleOAuthClient.verifyAndExtractIdToken("id-token-jwt", nonce)).thenReturn(idTokenPayload);

        when(userIdentityRepository.findByProviderAndProviderSubject("GOOGLE", sub)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        when(tenantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(unitRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByName("Proprietário")).thenReturn(Optional.empty());
        when(roleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hashed-random-pw");
        when(jwtUtil.generateToken(any(), any(), any(), any(), any())).thenReturn("gomech-jwt-token");

        AuthResponse response = googleOAuthService.authenticate(
                new GoogleOAuthCallbackRequest(code, state),
                "127.0.0.1", "Safari", "iPhone"
        );

        assertThat(response.accessToken()).isEqualTo("gomech-jwt-token");
        verify(tenantRepository).save(any(Tenant.class));
        verify(unitRepository).save(any(Unit.class));
        verify(userRepository, atLeastOnce()).save(any(User.class));
        verify(userIdentityRepository).save(any(UserIdentity.class));
    }
}
