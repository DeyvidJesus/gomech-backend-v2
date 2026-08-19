package com.gomech.api.modules.iam.infrastructure.persistence.repository;

import com.gomech.api.modules.iam.infrastructure.persistence.model.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findByName(String name);

    @Override
    @EntityGraph(attributePaths = {"permissions"})
    List<Role> findAll();
}
