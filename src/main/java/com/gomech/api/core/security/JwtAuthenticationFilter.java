package com.gomech.api.core.security;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.core.tenancy.UnitContextHolder;
import com.gomech.api.core.tenancy.UnitReference;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns a verified access token into Spring Security authentication plus the trusted request context
 * that {@code ActorContext} is later derived from.
 *
 * <p>Everything established here comes from a token whose signature and expiry have been checked, so
 * it is trusted: the tenant overrides any caller-provided selection, and the unit and authorities are
 * taken from the token's claims.
 *
 * <p>A request without a token, or with one that fails verification, is left unauthenticated. It is
 * not rejected here — that is the security filter chain's decision — but no context is established
 * from it.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader != null
                && authHeader.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(authHeader.substring(BEARER_PREFIX.length()), request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String jwt, HttpServletRequest request) {
        try {
            String userId = jwtUtil.extractUserId(jwt);
            if (userId == null || !jwtUtil.isTokenValid(jwt, UUID.fromString(userId))) {
                log.debug("Rejected access token: failed validation");
                return;
            }

            establishTenant(jwt);
            establishUnit(jwt);

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    authoritiesFrom(jwt)
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (JwtException | IllegalArgumentException e) {
            // An unreadable, tampered, or expired token leaves the request unauthenticated. The
            // security filter chain rejects it downstream; the reason is logged rather than swallowed.
            log.debug("Rejected access token: {}", e.getMessage());
        }
    }

    /** The token's tenant is proven, so it replaces any tenant the caller merely asked for. */
    private void establishTenant(String jwt) {
        UUID tenantId = jwtUtil.extractTenantId(jwt);
        if (tenantId != null) {
            TenantContextHolder.setAuthenticatedTenant(tenantId);
        }
    }

    private void establishUnit(String jwt) {
        UUID unitId = jwtUtil.extractUnitId(jwt);
        if (unitId != null) {
            UnitContextHolder.setUnit(UnitReference.of(unitId));
        }
    }

    /**
     * Maps the token's claims onto Spring authorities using the framework's own convention: roles are
     * prefixed with {@code ROLE_}, permissions are carried verbatim. Absent claims yield no
     * authorities, which is exactly how tokens issued today behave.
     */
    private List<GrantedAuthority> authoritiesFrom(String jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : jwtUtil.extractRoles(jwt)) {
            authorities.add(new SimpleGrantedAuthority(ROLE_AUTHORITY_PREFIX + role));
        }
        for (String permission : jwtUtil.extractPermissions(jwt)) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        return authorities;
    }
}
