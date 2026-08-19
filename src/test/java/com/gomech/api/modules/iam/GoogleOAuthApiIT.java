package com.gomech.api.modules.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.core.security.OAuthStateUtil;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.GoogleAuthorizeUrlResponse;
import com.gomech.api.modules.iam.api.dto.GoogleOAuthCallbackRequest;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
import com.gomech.api.modules.iam.application.GoogleOAuthClient;
import com.gomech.api.modules.iam.infrastructure.oauth.GoogleIdTokenPayload;
import com.gomech.api.modules.iam.infrastructure.oauth.GoogleTokenResponse;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserIdentityRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class GoogleOAuthApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private OAuthStateUtil oAuthStateUtil;

    @MockBean
    private GoogleOAuthClient googleOAuthClient;

    @Test
    @DisplayName("Should initiate Google authorization flow returning authorization URL and signed state")
    void shouldInitiateGoogleAuthorizeFlow() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/google/authorize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").isNotEmpty())
                .andExpect(jsonPath("$.state").isNotEmpty());
    }

    @Test
    @DisplayName("Should authenticate new Google user and auto-provision organization and identity")
    void shouldAuthenticateNewGoogleUser() throws Exception {
        // 1. Obter estado assinado
        String authorizeResponseJson = mockMvc.perform(get("/api/v1/auth/oauth/google/authorize"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GoogleAuthorizeUrlResponse authUrlResponse = objectMapper.readValue(authorizeResponseJson, GoogleAuthorizeUrlResponse.class);
        String signedState = authUrlResponse.state();
        OAuthStateUtil.OAuthStateData stateData = oAuthStateUtil.validateAndExtractState(signedState);

        String googleSub = "google-sub-it-001";
        String googleEmail = "google.user001@gmail.com";

        // 2. Mockar troca de código e ID Token
        GoogleTokenResponse tokenResponse = new GoogleTokenResponse("mock-access", 3600L, "Bearer", "openid email", "mock-id-token", null);
        when(googleOAuthClient.exchangeCode(eq("valid-auth-code"), anyString(), anyString())).thenReturn(tokenResponse);

        GoogleIdTokenPayload idTokenPayload = new GoogleIdTokenPayload(googleSub, googleEmail, true, "Google User 001", null, stateData.nonce());
        when(googleOAuthClient.verifyAndExtractIdToken("mock-id-token", stateData.nonce())).thenReturn(idTokenPayload);

        // 3. Executar callback
        GoogleOAuthCallbackRequest callbackRequest = new GoogleOAuthCallbackRequest("valid-auth-code", signedState);

        String callbackResponseJson = mockMvc.perform(post("/api/v1/auth/oauth/google/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callbackRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(googleEmail))
                .andReturn()
                .getResponse()
                .getContentAsString();

        AuthResponse authResponse = objectMapper.readValue(callbackResponseJson, AuthResponse.class);
        assertThat(authResponse.user().email()).isEqualTo(googleEmail);

        // 4. Verificar persistência no banco
        assertThat(userRepository.existsByEmail(googleEmail)).isTrue();
        assertThat(userIdentityRepository.existsByProviderAndProviderSubject("GOOGLE", googleSub)).isTrue();
    }

    @Test
    @DisplayName("Should link Google identity to an existing email/password registered account")
    void shouldLinkGoogleIdentityToExistingEmailUser() throws Exception {
        String existingEmail = "existing.owner@oficina.com.br";

        // 1. Cadastrar oficina e usuário previamente via e-mail e senha
        RegisterWorkshopRequest registerRequest = new RegisterWorkshopRequest(
                "Oficina Auto Link",
                "Rua dos Testes, 100",
                3,
                List.of("Mecânica"),
                "Proprietário Link",
                existingEmail,
                "Password@123"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // 2. Iniciar fluxo Google OAuth
        String authorizeResponseJson = mockMvc.perform(get("/api/v1/auth/oauth/google/authorize"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GoogleAuthorizeUrlResponse authUrlResponse = objectMapper.readValue(authorizeResponseJson, GoogleAuthorizeUrlResponse.class);
        String signedState = authUrlResponse.state();
        OAuthStateUtil.OAuthStateData stateData = oAuthStateUtil.validateAndExtractState(signedState);

        String googleSub = "google-sub-existing-002";

        // 3. Mockar ID token com o mesmo e-mail do usuário existente
        GoogleTokenResponse tokenResponse = new GoogleTokenResponse("mock-access", 3600L, "Bearer", "openid email", "mock-id-token", null);
        when(googleOAuthClient.exchangeCode(eq("code-link"), anyString(), anyString())).thenReturn(tokenResponse);

        GoogleIdTokenPayload idTokenPayload = new GoogleIdTokenPayload(googleSub, existingEmail, true, "Proprietário Link", null, stateData.nonce());
        when(googleOAuthClient.verifyAndExtractIdToken("mock-id-token", stateData.nonce())).thenReturn(idTokenPayload);

        // 4. Executar callback
        GoogleOAuthCallbackRequest callbackRequest = new GoogleOAuthCallbackRequest("code-link", signedState);

        mockMvc.perform(post("/api/v1/auth/oauth/google/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callbackRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(existingEmail));

        // 5. Verificar que a identidade Google foi vinculada ao mesmo usuário existente
        var linkedIdentity = userIdentityRepository.findByProviderAndProviderSubject("GOOGLE", googleSub);
        assertThat(linkedIdentity).isPresent();
        assertThat(linkedIdentity.get().getUser().getEmail()).isEqualTo(existingEmail);
    }

    @Test
    @DisplayName("Should reject OAuth callback with tampered or invalid state")
    void shouldRejectCallbackWithTamperedState() throws Exception {
        GoogleOAuthCallbackRequest callbackRequest = new GoogleOAuthCallbackRequest("some-code", "tampered-state-signature");

        mockMvc.perform(post("/api/v1/auth/oauth/google/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callbackRequest)))
                .andExpect(status().isUnauthorized());
    }
}
