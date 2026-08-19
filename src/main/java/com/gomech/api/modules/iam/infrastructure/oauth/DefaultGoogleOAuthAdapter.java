package com.gomech.api.modules.iam.infrastructure.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.iam.application.GoogleOAuthClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
public class DefaultGoogleOAuthAdapter implements GoogleOAuthClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${gomech.oauth.google.client-id}")
    private String clientId;

    @Value("${gomech.oauth.google.client-secret}")
    private String clientSecret;

    @Value("${gomech.oauth.google.token-uri:https://oauth2.googleapis.com/token}")
    private String tokenUri;

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultGoogleOAuthAdapter(ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().build();
        this.objectMapper = objectMapper;
    }

    public DefaultGoogleOAuthAdapter(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public GoogleTokenResponse exchangeCode(String code, String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("code", code);
        formData.add("code_verifier", codeVerifier);
        formData.add("grant_type", "authorization_code");
        formData.add("redirect_uri", redirectUri);

        try {
            return restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (Exception e) {
            log.error("Falha ao trocar código de autorização Google OAuth: {}", e.getMessage());
            throw new SecurityException("Falha na comunicação com o provedor Google OAuth", e);
        }
    }

    @Override
    public GoogleIdTokenPayload verifyAndExtractIdToken(String idToken, String expectedNonce) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new SecurityException("Formato inválido de ID Token");
            }

            byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payloadJson = new String(decodedBytes, StandardCharsets.UTF_8);
            JsonNode claims = objectMapper.readTree(payloadJson);

            // 1. Validar emissor (iss)
            String iss = claims.path("iss").asText();
            if (!List.of("https://accounts.google.com", "accounts.google.com").contains(iss)) {
                throw new SecurityException("Emissor inválido no ID Token: " + iss);
            }

            // 2. Validar audiência (aud)
            String aud = claims.path("aud").asText();
            if (!clientId.equals(aud)) {
                throw new SecurityException("Audiência divergente no ID Token: " + aud);
            }

            // 3. Validar expiração (exp)
            long exp = claims.path("exp").asLong();
            if (Instant.now().getEpochSecond() > exp) {
                throw new SecurityException("ID Token expirado em: " + exp);
            }

            // 4. Validar nonce
            String tokenNonce = claims.path("nonce").asText();
            if (expectedNonce != null && !expectedNonce.equals(tokenNonce)) {
                throw new SecurityException("Nonce divergente no ID Token");
            }

            // 5. Validar e-mail verificado
            boolean emailVerified = claims.path("email_verified").asBoolean(false);
            if (!emailVerified) {
                throw new SecurityException("O e-mail do Google não está verificado");
            }

            String sub = claims.path("sub").asText();
            String email = claims.path("email").asText();
            String name = claims.path("name").asText(null);
            String picture = claims.path("picture").asText(null);

            if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
                throw new SecurityException("Claims essenciais (sub, email) ausentes no ID Token");
            }

            return new GoogleIdTokenPayload(sub, email, emailVerified, name, picture, tokenNonce);
        } catch (SecurityException e) {
            log.warn("Rejeição na validação do ID Token: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao decodificar ID Token: {}", e.getMessage());
            throw new SecurityException("Erro na validação do token de identidade", e);
        }
    }
}
