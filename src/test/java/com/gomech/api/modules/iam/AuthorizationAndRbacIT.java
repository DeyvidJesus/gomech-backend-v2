package com.gomech.api.modules.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.iam.api.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class AuthorizationAndRbacIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Deve provisionar 4 papéis padrão no onboarding e permitir acesso total ao Proprietário")
    void shouldProvisionDefaultRolesAndAllowOwnerFullAccess() throws Exception {
        String email = "dono-" + UUID.randomUUID() + "@oficina.com.br";
        RegisterWorkshopRequest reg = new RegisterWorkshopRequest(
                "Oficina RBAC Master",
                "Av. Paulista, 1000",
                4,
                List.of("Mecânica Geral"),
                "Carlos Dono",
                email,
                "senhaForte123"
        );

        String regJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AuthResponse authResponse = objectMapper.readValue(regJson, AuthResponse.class);
        String token = authResponse.accessToken();

        // 1. Listar papéis provisionados
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.name == 'Proprietário')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Gerente')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Mecânico')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Atendente')]").exists());

        // 2. Listar catálogo global de permissões
        mockMvc.perform(get("/api/v1/roles/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'IAM_USER_READ')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'OPERATIONS_ORDER_EXECUTE')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'FINANCE_TRANSACTION_WRITE')]").exists());
    }

    @Test
    @DisplayName("Deve criar nova filial e realizar troca de unidade ativa sem reautenticação")
    void shouldCreateBranchUnitAndSwitchActiveUnitSeamlessly() throws Exception {
        String email = "dono-switch-" + UUID.randomUUID() + "@oficina.com.br";
        RegisterWorkshopRequest reg = new RegisterWorkshopRequest(
                "Oficina Multi-Filial",
                "Rua Matriz, 10",
                4,
                List.of("Mecânica Geral"),
                "Eduardo Dono",
                email,
                "senhaForte123"
        );

        String regJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AuthResponse authResponse = objectMapper.readValue(regJson, AuthResponse.class);
        String token = authResponse.accessToken();

        // Upgrade para plano PRO para liberar limite de filiais
        mockMvc.perform(post("/api/v1/billing/subscription/change-plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"PRO\"}"))
                .andExpect(status().isOk());

        // Criar Filial Zona Sul
        CreateUnitRequest branchReq = new CreateUnitRequest("Filial Zona Sul", "Av. Sul, 200", false);
        String unitJson = mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(branchReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        UnitResponse branch = objectMapper.readValue(unitJson, UnitResponse.class);

        // Realizar switch-unit para a Filial Zona Sul
        SwitchUnitRequest switchReq = new SwitchUnitRequest(branch.id());
        String switchJson = mockMvc.perform(post("/api/v1/auth/switch-unit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(switchReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AuthResponse switchedAuth = objectMapper.readValue(switchJson, AuthResponse.class);
        assertThat(switchedAuth.accessToken()).isNotBlank();
        assertThat(switchedAuth.user().activeUnitId()).isEqualTo(branch.id());

        // Usar novo token para consultar unidade
        mockMvc.perform(get("/api/v1/units/" + branch.id())
                        .header("Authorization", "Bearer " + switchedAuth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Filial Zona Sul"));
    }

    @Test
    @DisplayName("Deve barrar usuário com perfil restrito de acessar endpoints administrativos (PBAC 403 Forbidden)")
    void shouldEnforceRbacPermissionsAndRejectUnauthorizedEndpoints() throws Exception {
        // 1. Onboarding Dono
        String ownerEmail = "dono-rbac-" + UUID.randomUUID() + "@oficina.com.br";
        RegisterWorkshopRequest reg = new RegisterWorkshopRequest(
                "Oficina RBAC Strict",
                "Rua Principal, 1",
                4,
                List.of("Mecânica Geral"),
                "Roberto Dono",
                ownerEmail,
                "senhaForte123"
        );

        String regJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AuthResponse ownerAuth = objectMapper.readValue(regJson, AuthResponse.class);
        String ownerToken = ownerAuth.accessToken();

        // 2. Buscar papel 'Mecânico'
        String rolesJson = mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<RoleResponse> roles = objectMapper.readValue(rolesJson, objectMapper.getTypeFactory().constructCollectionType(List.class, RoleResponse.class));
        RoleResponse mechanicRole = roles.stream().filter(r -> r.name().equals("Mecânico")).findFirst().orElseThrow();

        // 3. Cadastrar usuário mecânico
        String mechanicEmail = "mecanico-" + UUID.randomUUID() + "@oficina.com.br";
        CreateUserRequest userReq = new CreateUserRequest(
                "Marcos Mecânico",
                mechanicEmail,
                "mecanico123",
                List.of(new CreateUserRequest.RoleAssignmentDto(mechanicRole.id(), ownerAuth.user().activeUnitId()))
        );

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isCreated());

        // 4. Login do Mecânico
        LoginRequest loginReq = new LoginRequest(mechanicEmail, "mecanico123");
        String loginJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AuthResponse mechanicAuth = objectMapper.readValue(loginJson, AuthResponse.class);
        String mechanicToken = mechanicAuth.accessToken();

        // 5. Mecânico tenta criar um papel customizado -> Deve receber 403 Forbidden!
        CreateRoleRequest customRoleReq = new CreateRoleRequest("Papel Hacker", "Tentativa", List.of("IAM_USER_READ"));
        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + mechanicToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customRoleReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve rejeitar tentativa de switch-unit para unidade de outro tenant")
    void shouldRejectCrossTenantUnitSwitch() throws Exception {
        // Criar Tenant A
        RegisterWorkshopRequest regA = new RegisterWorkshopRequest(
                "Oficina A", "Rua A", 2, List.of("Geral"), "Dono A",
                "donoA-" + UUID.randomUUID() + "@oficina.com.br", "senhaForte123"
        );
        String regAJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regA)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        AuthResponse authA = objectMapper.readValue(regAJson, AuthResponse.class);

        // Criar Tenant B
        RegisterWorkshopRequest regB = new RegisterWorkshopRequest(
                "Oficina B", "Rua B", 2, List.of("Geral"), "Dono B",
                "donoB-" + UUID.randomUUID() + "@oficina.com.br", "senhaForte123"
        );
        String regBJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regB)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        AuthResponse authB = objectMapper.readValue(regBJson, AuthResponse.class);

        // Usuário do Tenant A tenta alternar para a unidade do Tenant B
        SwitchUnitRequest crossTenantSwitch = new SwitchUnitRequest(authB.user().activeUnitId());
        mockMvc.perform(post("/api/v1/auth/switch-unit")
                        .header("Authorization", "Bearer " + authA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crossTenantSwitch)))
                .andExpect(status().isBadRequest());
    }
}
