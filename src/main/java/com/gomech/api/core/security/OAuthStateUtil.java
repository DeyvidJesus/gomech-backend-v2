package com.gomech.api.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class OAuthStateUtil {

    private final SecretKey signingKey;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final long STATE_EXPIRATION_MS = 5 * 60 * 1000; // 5 minutos

    public OAuthStateUtil(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public record OAuthStateData(
            String stateId,
            String nonce,
            String codeVerifier,
            String redirectUri
    ) {}

    public record GeneratedOAuthContext(
            String signedState,
            String nonce,
            String codeVerifier,
            String codeChallenge
    ) {}

    public GeneratedOAuthContext generateContext(String redirectUri) {
        String stateId = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        Date now = new Date();
        Date expiry = new Date(now.getTime() + STATE_EXPIRATION_MS);

        String signedState = Jwts.builder()
                .subject("oauth_state")
                .id(stateId)
                .claim("nonce", nonce)
                .claim("codeVerifier", codeVerifier)
                .claim("redirectUri", redirectUri)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();

        return new GeneratedOAuthContext(signedState, nonce, codeVerifier, codeChallenge);
    }

    public OAuthStateData validateAndExtractState(String signedState) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(signedState)
                    .getPayload();

            String stateId = claims.getId();
            String nonce = claims.get("nonce", String.class);
            String codeVerifier = claims.get("codeVerifier", String.class);
            String redirectUri = claims.get("redirectUri", String.class);

            if (stateId == null || nonce == null || codeVerifier == null) {
                throw new SecurityException("Estado OAuth inválido ou incompleto");
            }

            return new OAuthStateData(stateId, nonce, codeVerifier, redirectUri);
        } catch (Exception e) {
            throw new SecurityException("Assinatura de estado OAuth inválida ou expirada: " + e.getMessage(), e);
        }
    }

    public String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 não disponível", e);
        }
    }
}
