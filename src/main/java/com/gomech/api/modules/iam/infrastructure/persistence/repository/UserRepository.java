package com.gomech.api.modules.iam.infrastructure.persistence.repository;

import com.gomech.api.modules.iam.infrastructure.persistence.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    // O EntityGraph resolve o problema de N+1 Queries instruindo o Hibernate a usar JOINs para carregar associações lazy em uma única query.
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role", "userRoles.role.permissions", "userRoles.unit"})
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"userRoles", "userRoles.role", "userRoles.role.permissions", "userRoles.unit"})
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") UUID id);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = {"userRoles", "userRoles.role", "userRoles.unit"})
    List<User> findAllByTenantId(UUID tenantId);
}
