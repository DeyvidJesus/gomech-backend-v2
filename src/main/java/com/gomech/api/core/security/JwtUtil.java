package com.gomech.api.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public UUID extractTenantId(String token) {
        String tenantIdStr = extractClaim(token, claims -> claims.get("tenantId", String.class));
        return tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
    }

    /**
     * The unit the token is scoped to, or null when it carries none. Optional: tokens issued before
     * unit scoping exists simply omit the claim.
     */
    public UUID extractUnitId(String token) {
        String unitIdStr = extractClaim(token, claims -> claims.get("unitId", String.class));
        return unitIdStr != null ? UUID.fromString(unitIdStr) : null;
    }

    /** Role names carried by the token, empty when the claim is absent. */
    public List<String> extractRoles(String token) {
        return extractStringList(token, "roles");
    }

    /** Permission names carried by the token, empty when the claim is absent. */
    public List<String> extractPermissions(String token) {
        return extractStringList(token, "permissions");
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(String token, String claimName) {
        List<?> values = extractClaim(token, claims -> claims.get(claimName, List.class));
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UUID userId, UUID tenantId) {
        return generateToken(userId, tenantId, null, List.of(), List.of());
    }

    /**
     * Issues a token that also carries unit scope and the actor's authorities.
     *
     * <p>Additive: the extra claims are omitted when null or empty, so a token issued through the
     * two-argument overload is byte-for-byte what it was before. Callers opt in when they have the
     * information to supply.
     */
    public String generateToken(
            UUID userId,
            UUID tenantId,
            UUID unitId,
            Collection<String> roles,
            Collection<String> permissions
    ) {
        Map<String, Object> extraClaims = new HashMap<>();
        if (tenantId != null) {
            extraClaims.put("tenantId", tenantId.toString());
        }
        if (unitId != null) {
            extraClaims.put("unitId", unitId.toString());
        }
        if (roles != null && !roles.isEmpty()) {
            extraClaims.put("roles", List.copyOf(roles));
        }
        if (permissions != null && !permissions.isEmpty()) {
            extraClaims.put("permissions", List.copyOf(permissions));
        }
        return buildToken(extraClaims, userId.toString(), jwtExpiration);
    }

    public String extractTokenId(String token) {
        return extractClaim(token, Claims::getId);
    }

    public Date extractIssuedAt(String token) {
        return extractClaim(token, Claims::getIssuedAt);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(String token, UUID userId) {
        final String extractedUserId = extractUserId(token);
        return (extractedUserId.equals(userId.toString())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
