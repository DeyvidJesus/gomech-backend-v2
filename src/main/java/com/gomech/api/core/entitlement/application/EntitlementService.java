package com.gomech.api.core.entitlement.application;

import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.entitlement.api.EntitlementSnapshot;

public interface EntitlementService {

    EntitlementSnapshot resolve(ActorContext actor);
}
