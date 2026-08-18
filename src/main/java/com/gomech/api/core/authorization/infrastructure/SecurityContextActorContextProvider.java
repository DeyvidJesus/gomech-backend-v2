package com.gomech.api.core.authorization.infrastructure;

import com.gomech.api.core.authorization.application.ActorContextProvider;
import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.core.tenancy.UnitContextHolder;
import com.gomech.api.core.tenancy.UnitReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the {@link ActorContext} from the state established by the authentication filter.
 *
 * <p>Every field comes from something already verified: the principal from the Spring Security
 * context, the tenant only when {@link TenantContextHolder} reports it as trusted, and the unit from
 * the token claim. A caller-provided tenant selection is deliberately not used — an actor assembled
 * from an unauthenticated header would not be an actor at all.
 */
@Component
public class SecurityContextActorContextProvider implements ActorContextProvider {

    private static final Logger log = LoggerFactory.getLogger(SecurityContextActorContextProvider.class);

    private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    @Override
    public Optional<ActorContext> currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        UUID userId = parseUserId(authentication.getName());
        if (userId == null) {
            return Optional.empty();
        }

        return Optional.of(new ActorContext(
            userId,
            trustedTenantId(),
            currentUnit(),
            authoritiesWithPrefix(authentication),
            authoritiesWithoutPrefix(authentication)
        ));
    }

    /**
     * Only a proven tenant belongs on an actor. A tenant the caller merely selected stays out, even
     * though it remains in scope for tenant-filtered reads on public endpoints.
     */
    private UUID trustedTenantId() {
        return TenantContextHolder.isTrusted() ? TenantContextHolder.getTenantId() : null;
    }

    private UnitReference currentUnit() {
        return UnitContextHolder.getUnit().orElse(null);
    }

    private Set<String> authoritiesWithPrefix(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith(ROLE_AUTHORITY_PREFIX))
            .map(authority -> authority.substring(ROLE_AUTHORITY_PREFIX.length()))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> authoritiesWithoutPrefix(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> !authority.startsWith(ROLE_AUTHORITY_PREFIX))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private UUID parseUserId(String principal) {
        if (principal == null || principal.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(principal);
        } catch (IllegalArgumentException e) {
            log.debug("Authenticated principal '{}' is not a user id, no actor context derived", principal);
            return null;
        }
    }
}
