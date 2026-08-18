package com.gomech.api.core.entitlement.infrastructure;

import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.entitlement.api.EntitlementSnapshot;
import com.gomech.api.core.entitlement.application.EntitlementService;
import org.springframework.stereotype.Component;

/**
 * V1 PLACEHOLDER. Reports exactly what the actor already carries, and nothing more.
 *
 * <p>The actor's permissions become the snapshot's permissions and the actor's roles become its
 * scopes. No plan, subscription or feature flag is consulted, because none of that exists yet. So a
 * permission present here means only "the access token said so" — it is not evidence that the
 * tenant's plan includes the feature.
 *
 * <p>It is deliberately a pass-through rather than a fabricated set: returning invented entitlements
 * would be worse than returning the truth of what is currently known.
 *
 * <h2>Intended replacement</h2>
 *
 * <p>The real implementation resolves entitlements from the tenant's subscription and plan — the
 * billing module's concern per DATABASE_DESIGN — and intersects them with the actor's permissions,
 * so a permission granted by a role can still be withheld by the plan. It replaces this bean without
 * changing callers, which depend on {@link EntitlementService}.
 */
@Component
public class StaticEntitlementService implements EntitlementService {

    @Override
    public EntitlementSnapshot resolve(ActorContext actor) {
        return new EntitlementSnapshot(actor.permissions(), actor.roles());
    }
}
