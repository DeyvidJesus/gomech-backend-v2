package com.gomech.api.modules.iam.application;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.CreateRoleRequest;
import com.gomech.api.modules.iam.api.dto.PermissionResponse;
import com.gomech.api.modules.iam.api.dto.RoleResponse;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Permission;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Role;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.PermissionRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional
    public Role provisionDefaultRoles(UUID tenantId) {
        List<Permission> allPermissions = permissionRepository.findAll();
        Map<String, Permission> permissionMap = allPermissions.stream()
                .collect(Collectors.toMap(Permission::getCode, p -> p, (p1, p2) -> p1));

        // 1. Proprietário (Acesso total)
        Role ownerRole = createOrUpdateRole(tenantId, "Proprietário", "Acesso total à gestão da oficina", new HashSet<>(allPermissions));

        // 2. Gerente
        Set<String> managerPermissionCodes = Set.of(
                "IAM_USER_READ", "IAM_USER_WRITE", "IAM_UNIT_READ",
                "CRM_CUSTOMER_READ", "CRM_CUSTOMER_WRITE", "CRM_CUSTOMER_DELETE", "CRM_VEHICLE_READ", "CRM_VEHICLE_WRITE", "CRM_VEHICLE_DELETE",
                "OPERATIONS_ORDER_READ", "OPERATIONS_ORDER_WRITE", "OPERATIONS_ORDER_EXECUTE", "OPERATIONS_ORDER_CLOSE", "OPERATIONS_ORDER_CANCEL",
                "OPERATIONS_APPOINTMENT_READ", "OPERATIONS_APPOINTMENT_WRITE", "OPERATIONS_APPOINTMENT_CANCEL",
                "OPERATIONS_INSPECTION_READ", "OPERATIONS_INSPECTION_WRITE", "OPERATIONS_INSPECTION_EXECUTE",
                "OPERATIONS_QUOTE_READ", "OPERATIONS_QUOTE_WRITE", "OPERATIONS_QUOTE_APPROVE", "OPERATIONS_QUOTE_SEND", "OPERATIONS_QUOTE_CANCEL",
                "INVENTORY_PRODUCT_READ", "INVENTORY_PRODUCT_WRITE", "INVENTORY_MOVEMENT_WRITE",
                "FINANCE_TRANSACTION_READ", "FINANCE_TRANSACTION_WRITE"
        );
        createOrUpdateRole(tenantId, "Gerente", "Gestão operacional, clientes e financeira da unidade", mapPermissions(managerPermissionCodes, permissionMap));

        // 3. Mecânico
        Set<String> mechanicPermissionCodes = Set.of(
                "CRM_VEHICLE_READ",
                "OPERATIONS_ORDER_READ", "OPERATIONS_ORDER_EXECUTE",
                "OPERATIONS_APPOINTMENT_READ",
                "OPERATIONS_INSPECTION_READ", "OPERATIONS_INSPECTION_WRITE", "OPERATIONS_INSPECTION_EXECUTE",
                "OPERATIONS_QUOTE_READ", "OPERATIONS_QUOTE_WRITE",
                "INVENTORY_PRODUCT_READ"
        );
        createOrUpdateRole(tenantId, "Mecânico", "Execução técnica de ordens de serviço e consulta de veículos", mapPermissions(mechanicPermissionCodes, permissionMap));

        // 4. Atendente / Recepcionista
        Set<String> attendantPermissionCodes = Set.of(
                "CRM_CUSTOMER_READ", "CRM_CUSTOMER_WRITE", "CRM_VEHICLE_READ", "CRM_VEHICLE_WRITE",
                "OPERATIONS_ORDER_READ", "OPERATIONS_ORDER_WRITE", "OPERATIONS_ORDER_CANCEL",
                "OPERATIONS_APPOINTMENT_READ", "OPERATIONS_APPOINTMENT_WRITE", "OPERATIONS_APPOINTMENT_CANCEL",
                "OPERATIONS_INSPECTION_READ", "OPERATIONS_INSPECTION_WRITE",
                "OPERATIONS_QUOTE_READ", "OPERATIONS_QUOTE_WRITE", "OPERATIONS_QUOTE_SEND", "OPERATIONS_QUOTE_CANCEL",
                "INVENTORY_PRODUCT_READ",
                "FINANCE_TRANSACTION_READ"
        );
        createOrUpdateRole(tenantId, "Atendente", "Recepção de clientes, abertura de orçamentos e agendamentos", mapPermissions(attendantPermissionCodes, permissionMap));

        log.info("Papéis padrão provisionados com sucesso para o tenant {}", tenantId);
        return ownerRole;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getRoles(UUID tenantId) {
        return roleRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(p -> new PermissionResponse(p.getId(), p.getCode(), p.getModule()))
                .toList();
    }

    @Transactional
    public RoleResponse createCustomRole(CreateRoleRequest request, UUID tenantId) {
        UUID effectiveTenantId = tenantId != null ? tenantId : TenantContextHolder.getTenantId();

        if (roleRepository.findByName(request.name()).isPresent()) {
            throw new IllegalArgumentException("Já existe um papel com o nome '" + request.name() + "' nesta oficina");
        }

        List<Permission> permissions = permissionRepository.findByCodeIn(request.permissionCodes());
        if (permissions.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma permissão válida encontrada para os códigos informados");
        }

        Role role = new Role();
        role.setTenantId(effectiveTenantId);
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(new HashSet<>(permissions));

        Role savedRole = roleRepository.save(role);
        return toResponse(savedRole);
    }

    private Role createOrUpdateRole(UUID tenantId, String name, String description, Set<Permission> permissions) {
        Role role = roleRepository.findByName(name).orElseGet(() -> {
            Role newRole = new Role();
            newRole.setTenantId(tenantId);
            newRole.setName(name);
            return newRole;
        });

        role.setDescription(description);
        role.setPermissions(permissions);
        return roleRepository.saveAndFlush(role);
    }

    private Set<Permission> mapPermissions(Set<String> codes, Map<String, Permission> map) {
        return codes.stream()
                .map(map::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private RoleResponse toResponse(Role role) {
        List<String> permissionCodes = role.getPermissions().stream()
                .map(Permission::getCode)
                .sorted()
                .toList();

        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getTenantId(),
                permissionCodes
        );
    }
}
