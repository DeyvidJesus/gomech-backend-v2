package com.gomech.api.core.authorization.application;

import com.gomech.api.core.authorization.api.ActorContext;

import java.util.Optional;

/**
 * Supplies the {@link ActorContext} for the request in progress.
 *
 * <p>This is the contract modules depend on. It lives in {@code application} so a module binds to the
 * abstraction rather than to the implementation that reads Spring Security
 * ({@code modules_must_not_depend_on_core_infrastructure}).
 *
 * <p>An unauthenticated request is a normal, representable state and yields an empty result rather
 * than a partially populated actor. Deciding what an absent actor means is the caller's business.
 */
public interface ActorContextProvider {

    Optional<ActorContext> currentActor();
}
