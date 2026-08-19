package com.gomech.api.modules.iam;

import com.gomech.api.modules.iam.api.dto.CreateRoleRequest;
import com.gomech.api.modules.iam.api.dto.PermissionResponse;
import com.gomech.api.modules.iam.api.dto.RoleResponse;
import com.gomech.api.modules.iam.application.RoleService;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Permission;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Role;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.PermissionRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private RoleService roleService;

    private UUID tenantId;
    private Permission perm1;
    private Permission perm2;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        perm1 = new Permission();
        perm1.setId(UUID.randomUUID());
        perm1.setCode("IAM_USER_READ");
        perm1.setModule("IAM");

        perm2 = new Permission();
        perm2.setId(UUID.randomUUID());
        perm2.setCode("OPERATIONS_ORDER_EXECUTE");
        perm2.setModule("OPERATIONS");
    }

    @Test
    @DisplayName("Deve provisionar 4 papéis padrão com sucesso para o tenant")
    void shouldProvisionDefaultRolesSuccessfully() {
        when(permissionRepository.findAll()).thenReturn(List.of(perm1, perm2));
        when(roleRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(roleRepository.saveAndFlush(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        Role ownerRole = roleService.provisionDefaultRoles(tenantId);

        assertThat(ownerRole).isNotNull();
        assertThat(ownerRole.getName()).isEqualTo("Proprietário");
        verify(roleRepository, times(4)).saveAndFlush(any(Role.class));
    }

    @Test
    @DisplayName("Deve listar permissões do catálogo global")
    void shouldListAllPermissions() {
        when(permissionRepository.findAll()).thenReturn(List.of(perm1, perm2));

        List<PermissionResponse> permissions = roleService.getAllPermissions();
        assertThat(permissions).hasSize(2);
        assertThat(permissions.get(0).code()).isEqualTo("IAM_USER_READ");
    }

    @Test
    @DisplayName("Deve criar papel customizado com sucesso")
    void shouldCreateCustomRoleSuccessfully() {
        CreateRoleRequest request = new CreateRoleRequest(
                "Consultor Técnico",
                "Responsável por orçamentos complexos",
                List.of("OPERATIONS_ORDER_EXECUTE")
        );

        when(roleRepository.findByName("Consultor Técnico")).thenReturn(Optional.empty());
        when(permissionRepository.findByCodeIn(request.permissionCodes())).thenReturn(List.of(perm2));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        RoleResponse response = roleService.createCustomRole(request, tenantId);
        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Consultor Técnico");
        assertThat(response.permissions()).containsExactly("OPERATIONS_ORDER_EXECUTE");
    }

    @Test
    @DisplayName("Deve rejeitar criação de papel customizado com nome duplicado")
    void shouldRejectDuplicateRoleName() {
        CreateRoleRequest request = new CreateRoleRequest(
                "Gerente",
                "Descrição",
                List.of("IAM_USER_READ")
        );

        when(roleRepository.findByName("Gerente")).thenReturn(Optional.of(new Role()));

        assertThatThrownBy(() -> roleService.createCustomRole(request, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Já existe um papel");
    }
}
