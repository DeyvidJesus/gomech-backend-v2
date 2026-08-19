package com.gomech.api.modules.iam.infrastructure.persistence.repository;

import com.gomech.api.modules.iam.infrastructure.persistence.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);

    List<Permission> findAllByModule(String module);

    List<Permission> findByCodeIn(Collection<String> codes);
}
