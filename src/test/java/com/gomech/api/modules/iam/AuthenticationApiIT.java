package com.gomech.api.modules.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.LoginRequest;
import com.gomech.api.modules.iam.api.dto.RefreshTokenRequest;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
import com.gomech.api.modules.iam.infrastructure.persistence.model.UserSession;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthenticationApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("End-to-end IAM lifecycle: Registration, Login, Token Rotation, Reuse Detection, and Revocation")
    void testCompleteAuthenticationAndTokenLifecycle() throws Exception {
        // 1. Register a new workshop & owner user
        RegisterWorkshopRequest registerRequest = new RegisterWorkshopRequest(
                "Oficina Turbo Power",
                "Av. das Américas, 1000 - Rio de Janeiro",
                4,
                List.of("Mecânica Geral", "Injeção Eletrônica"),
                "Carlos Alberto",
                "carlos@turbopower.com.br",
                "Password@123"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("carlos@turbopower.com.br"))
                .andReturn();

        AuthResponse initialAuth = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String refreshToken1 = initialAuth.refreshToken();

        // 2. Authenticate via login
        LoginRequest loginRequest = new LoginRequest("carlos@turbopower.com.br", "Password@123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        AuthResponse loginAuth = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String loginRefreshToken = loginAuth.refreshToken();
        String accessToken = loginAuth.accessToken();

        // 3. Query active sessions with Bearer token
        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 4. Refresh token rotation (RTR) - normal flow
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(loginRefreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        AuthResponse rotatedAuth = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String rotatedRefreshToken = rotatedAuth.refreshToken();
        assertThat(rotatedRefreshToken).isNotEqualTo(loginRefreshToken);

        // Verify the old session is marked as revoked in DB
        Optional<UserSession> oldSession = userSessionRepository.findByRefreshToken(loginRefreshToken);
        assertThat(oldSession).isPresent();
        assertThat(oldSession.get().isRevoked()).isTrue();

        // 5. Token Reuse Attack Simulation: Presenting the already-rotated loginRefreshToken
        // This must trigger reuse detection and revoke the whole family
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(loginRefreshToken))))
                .andExpect(status().is4xxClientError());

        // Verify that the new rotated token was also revoked due to reuse detection
        Optional<UserSession> victimSession = userSessionRepository.findByRefreshToken(rotatedRefreshToken);
        assertThat(victimSession).isPresent();
        assertThat(victimSession.get().isRevoked()).isTrue();

        // 6. Logout single session
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken1))))
                .andExpect(status().isNoContent());

        Optional<UserSession> loggedOutSession = userSessionRepository.findByRefreshToken(refreshToken1);
        assertThat(loggedOutSession).isPresent();
        assertThat(loggedOutSession.get().isRevoked()).isTrue();
    }
}
