package com.gomech.api.modules.iam;

import com.gomech.api.core.entitlement.api.QuotaDecision;
import com.gomech.api.core.entitlement.application.EntitlementService;
import com.gomech.api.core.entitlement.domain.QuotaDimension;
import com.gomech.api.core.entitlement.domain.QuotaExceededException;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.AssignUserRoleRequest;
import com.gomech.api.modules.iam.api.dto.CreateUserRequest;
import com.gomech.api.modules.iam.api.dto.UserResponse;
import com.gomech.api.modules.iam.application.UserService;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Role;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Unit;
import com.gomech.api.modules.iam.infrastructure.persistence.model.User;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.RoleRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UnitRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EntitlementService entitlementService;

    @InjectMocks
    private UserService userService;

    private UUID tenantId;
    private UUID userId;
    private UUID roleId;
    private UUID unitId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);
    }

    @Test
    @DisplayName("Deve criar usuário quando cota de usuários estiver disponível")
    void shouldCreateUserSuccessfullyWhenQuotaAvailable() {
        CreateUserRequest request = new CreateUserRequest(
                "Carlos Atendente",
                "carlos@oficina.com.br",
                "SenhaForte@123",
                List.of()
        );

        when(userRepository.existsByEmail("carlos@oficina.com.br")).thenReturn(false);
        when(entitlementService.checkQuota(eq(tenantId), eq(QuotaDimension.USERS), eq(1L)))
                .thenReturn(QuotaDecision.allow(QuotaDimension.USERS, 1, 5, "allowed"));
        when(passwordEncoder.encode(any())).thenReturn("hashed-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(userId);
            return u;
        });

        UserResponse response = userService.createUser(request);
        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("carlos@oficina.com.br");
        verify(entitlementService).recordUsage(eq(tenantId), eq(QuotaDimension.USERS), eq(1L));
    }

    @Test
    @DisplayName("Deve rejeitar criação de usuário quando cota de usuários for excedida")
    void shouldRejectUserCreationWhenQuotaExceeded() {
        CreateUserRequest request = new CreateUserRequest(
                "Carlos Excedente",
                "carlos@oficina.com.br",
                "SenhaForte@123",
                List.of()
        );

        when(userRepository.existsByEmail("carlos@oficina.com.br")).thenReturn(false);
        when(entitlementService.checkQuota(eq(tenantId), eq(QuotaDimension.USERS), eq(1L)))
                .thenReturn(QuotaDecision.deny(QuotaDimension.USERS, 3, 3, "quota_exceeded"));

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("Limite de usuários ativos atingido");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve atribuir papel com escopo de unidade a um usuário com sucesso")
    void shouldAssignRoleToUserSuccessfully() {
        User user = new User();
        user.setId(userId);
        user.setName("João Mecânico");
        user.setEmail("joao@oficina.com.br");
        user.setTenantId(tenantId);

        Role role = new Role();
        role.setId(roleId);
        role.setName("Mecânico");
        role.setTenantId(tenantId);

        Unit unit = new Unit();
        unit.setId(unitId);
        unit.setName("Filial Sul");
        unit.setTenantId(tenantId);

        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AssignUserRoleRequest request = new AssignUserRoleRequest(roleId, unitId);
        UserResponse response = userService.assignRole(userId, request, tenantId);

        assertThat(response).isNotNull();
        assertThat(response.roles()).hasSize(1);
        assertThat(response.roles().get(0).roleName()).isEqualTo("Mecânico");
        assertThat(response.roles().get(0).unitName()).isEqualTo("Filial Sul");
    }

    @Test
    @DisplayName("Deve rejeitar atribuição de papel pertencente a outro tenant")
    void shouldRejectRoleFromAnotherTenant() {
        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);

        Role otherTenantRole = new Role();
        otherTenantRole.setId(roleId);
        otherTenantRole.setTenantId(UUID.randomUUID());

        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(otherTenantRole));

        AssignUserRoleRequest request = new AssignUserRoleRequest(roleId, null);

        assertThatThrownBy(() -> userService.assignRole(userId, request, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role não pertence à oficina");
    }
}
