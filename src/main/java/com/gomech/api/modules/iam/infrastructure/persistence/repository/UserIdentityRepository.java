package com.gomech.api.modules.iam.infrastructure.persistence.repository;

import com.gomech.api.modules.iam.infrastructure.persistence.model.UserIdentity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    @EntityGraph(attributePaths = {"user", "user.userRoles", "user.userRoles.role", "user.userRoles.role.permissions", "user.userRoles.unit"})
    Optional<UserIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);

    Optional<UserIdentity> findByUserIdAndProvider(UUID userId, String provider);

    List<UserIdentity> findAllByUserId(UUID userId);

    boolean existsByProviderAndProviderSubject(String provider, String providerSubject);
}
