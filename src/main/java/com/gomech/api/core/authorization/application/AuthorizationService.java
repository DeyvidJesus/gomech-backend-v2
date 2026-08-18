package com.gomech.api.core.authorization.application;

import com.gomech.api.core.authorization.api.AuthorizationRequest;
import com.gomech.api.core.authorization.api.AccessDecision;
import com.gomech.api.core.authorization.api.ActorContext;

public interface AuthorizationService {

    AccessDecision authorize(ActorContext actor, AuthorizationRequest request);
}
