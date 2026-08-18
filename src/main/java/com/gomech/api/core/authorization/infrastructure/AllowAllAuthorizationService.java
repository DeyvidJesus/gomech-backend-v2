package com.gomech.api.core.authorization.infrastructure;

import com.gomech.api.core.authorization.api.AccessDecision;
import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.authorization.api.AuthorizationRequest;
import com.gomech.api.core.authorization.application.AuthorizationService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * V1 PLACEHOLDER. Allows every action, for every actor, unconditionally.
 *
 * <p><strong>This is not an authorization guarantee.</strong> It exists so the
 * {@link AuthorizationService} contract has a binding and modules can be written against it, while
 * the decision logic is still to be built. Nothing about an allowed result here means the actor was
 * entitled to anything: the request is not inspected at all.
 *
 * <p>Every decision is stamped with {@link #PLACEHOLDER_REASON}, so an allow produced by this class
 * is distinguishable from a real one in logs, in tests and in an audit trail. A warning is also
 * logged once at startup, so an environment running without real authorization says so out loud
 * rather than looking secure by silence.
 *
 * <h2>Intended replacement</h2>
 *
 * <p>The real implementation evaluates the actor's permissions and roles — already carried on
 * {@link ActorContext} and resolvable through {@code EntitlementService} — against the action,
 * resource and attributes on the {@link AuthorizationRequest}, per the RBAC/PBAC model in
 * BACKEND_ARCHITECTURE §6 and DATABASE_DESIGN (permissions → roles → per-unit assignment). It
 * replaces this bean; callers do not change, because they depend on {@link AuthorizationService}.
 */
@Component
public class AllowAllAuthorizationService implements AuthorizationService {

    /** Marks a decision as produced by the placeholder rather than by evaluated policy. */
    public static final String PLACEHOLDER_REASON = "authorization_contract_placeholder";

    private static final Logger log = LoggerFactory.getLogger(AllowAllAuthorizationService.class);

    @PostConstruct
    void warnThatAuthorizationIsNotEnforced() {
        log.warn("Authorization is NOT enforced: {} is active and allows every action. "
                + "This is a V1 placeholder and must be replaced before any environment relies on "
                + "authorization decisions.",
            getClass().getSimpleName());
    }

    @Override
    public AccessDecision authorize(ActorContext actor, AuthorizationRequest request) {
        return AccessDecision.allow(PLACEHOLDER_REASON);
    }
}
