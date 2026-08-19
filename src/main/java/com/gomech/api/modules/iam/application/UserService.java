package com.gomech.api.modules.iam.application;

import com.gomech.api.core.entitlement.api.QuotaDecision;
import com.gomech.api.core.entitlement.application.EntitlementService;
import com.gomech.api.core.entitlement.domain.QuotaDimension;
import com.gomech.api.core.entitlement.domain.QuotaExceededException;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.AssignUserRoleRequest;
import com.gomech.api.modules.iam.api.dto.CreateUserRequest;
import com.gomech.api.modules.iam.api.dto.UserResponse;
import com.gomech.api.modules.iam.domain.UserStatus;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Role;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Unit;
import com.gomech.api.modules.iam.infrastructure.persistence.model.User;
import com.gomech.api.modules.iam.infrastructure.persistence.model.UserRole;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.RoleRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UnitRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UnitRepository unitRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntitlementService entitlementService;

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers(UUID tenantId) {
        UUID effectiveTenantId = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
        return userRepository.findAllByTenantId(effectiveTenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId, UUID tenantId) {
        UUID effectiveTenantId = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + userId));

        if (!user.getTenantId().equals(effectiveTenantId)) {
            throw new IllegalArgumentException("Usuário não pertence à oficina autenticada");
        }

        return toResponse(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado");
        }

        UUID tenantId = TenantContextHolder.getTenantId();

        // Avaliação de cota de usuários ativos via Core Entitlement
        QuotaDecision quotaDecision = entitlementService.checkQuota(tenantId, QuotaDimension.USERS, 1);
        if (!quotaDecision.allowed()) {
            throw new QuotaExceededException(
                    QuotaDimension.USERS,
                    quotaDecision.currentUsage(),
                    quotaDecision.limit(),
                    "Limite de usuários ativos atingido para o plano da oficina. Limite: " + quotaDecision.limit()
            );
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setTenantId(tenantId);
        user.setStatus(UserStatus.ACTIVE);

        if (request.roles() != null) {
            for (CreateUserRequest.RoleAssignmentDto assignment : request.roles()) {
                Role role = roleRepository.findById(assignment.roleId())
                        .orElseThrow(() -> new IllegalArgumentException("Role inválida: " + assignment.roleId()));

                if (!role.getTenantId().equals(tenantId)) {
                    throw new IllegalArgumentException("Role não pertence à oficina");
                }

                Unit unit = null;
                if (assignment.unitId() != null) {
                    unit = unitRepository.findById(assignment.unitId())
                            .orElseThrow(() -> new IllegalArgumentException("Unit inválida: " + assignment.unitId()));
                    if (!unit.getTenantId().equals(tenantId)) {
                        throw new IllegalArgumentException("Unit não pertence à oficina");
                    }
                }

                UserRole userRole = new UserRole();
                userRole.setUser(user);
                userRole.setRole(role);
                userRole.setUnit(unit);
                userRole.setTenantId(tenantId);

                user.getUserRoles().add(userRole);
            }
        }

        User savedUser = userRepository.save(user);
        entitlementService.recordUsage(tenantId, QuotaDimension.USERS, 1);
        return toResponse(savedUser);
    }

    @Transactional
    public UserResponse assignRole(UUID userId, AssignUserRoleRequest request, UUID tenantId) {
        UUID effectiveTenantId = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + userId));

        if (!user.getTenantId().equals(effectiveTenantId)) {
            throw new IllegalArgumentException("Usuário não pertence à oficina");
        }

        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new IllegalArgumentException("Role não encontrada: " + request.roleId()));

        if (!role.getTenantId().equals(effectiveTenantId)) {
            throw new IllegalArgumentException("Role não pertence à oficina");
        }

        Unit unit = null;
        if (request.unitId() != null) {
            unit = unitRepository.findById(request.unitId())
                    .orElseThrow(() -> new IllegalArgumentException("Unit não encontrada: " + request.unitId()));
            if (!unit.getTenantId().equals(effectiveTenantId)) {
                throw new IllegalArgumentException("Unit não pertence à oficina");
            }
        }

        final UUID targetUnitId = request.unitId();
        boolean alreadyAssigned = user.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole().getId().equals(role.getId())
                        && ((targetUnitId == null && ur.getUnit() == null)
                        || (targetUnitId != null && ur.getUnit() != null && ur.getUnit().getId().equals(targetUnitId))));

        if (alreadyAssigned) {
            throw new IllegalArgumentException("O usuário já possui este papel atribuído na unidade especificada");
        }

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setUnit(unit);
        userRole.setTenantId(effectiveTenantId);

        user.getUserRoles().add(userRole);
        User savedUser = userRepository.save(user);
        return toResponse(savedUser);
    }

    private UserResponse toResponse(User user) {
        List<UserResponse.UserRoleDetailDto> roleDetails = user.getUserRoles().stream()
                .map(ur -> new UserResponse.UserRoleDetailDto(
                        ur.getRole() != null ? ur.getRole().getId() : null,
                        ur.getRole() != null ? ur.getRole().getName() : null,
                        ur.getUnit() != null ? ur.getUnit().getId() : null,
                        ur.getUnit() != null ? ur.getUnit().getName() : "Todas as Unidades (Tenant-wide)"
                ))
                .toList();

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getStatus(),
                user.getTenantId(),
                roleDetails
        );
    }
}
