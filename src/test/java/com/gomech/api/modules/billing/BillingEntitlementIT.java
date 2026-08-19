package com.gomech.api.modules.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.core.entitlement.domain.QuotaDimension;
import com.gomech.api.modules.billing.api.dto.ChangePlanRequest;
import com.gomech.api.modules.billing.api.dto.RecordUsageRequest;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.CreateUnitRequest;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
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
class BillingEntitlementIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Deve provisionar assinatura TRIAL automaticamente no onboarding e consultar status")
    void shouldProvisionDefaultTrialSubscriptionOnOnboarding() throws Exception {
        String email = "billing-dono-" + UUID.randomUUID() + "@oficina.com.br";
        RegisterWorkshopRequest reg = new RegisterWorkshopRequest(
                "Oficina Entitlements",
                "Av. Brasil, 1500",
                4,
                List.of("Mecânica Geral"),
                "Dono Oficina",
                email,
                "SenhaForte@123"
        );

        String regJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AuthResponse auth = objectMapper.readValue(regJson, AuthResponse.class);
        String token = "Bearer " + auth.accessToken();

        // Consultar assinatura ativa
        mockMvc.perform(get("/api/v1/billing/subscription")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("TRIAL"))
                .andExpect(jsonPath("$.status").value("TRIALING"))
                .andExpect(jsonPath("$.features").isArray());
    }

    @Test
    @DisplayName("Deve listar todos os planos do catálogo de faturamento")
    void shouldListAllAvailableBillingPlans() throws Exception {
        mockMvc.perform(get("/api/v1/billing/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.code == 'TRIAL')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'STARTER')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'PRO')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'ENTERPRISE')]").exists());
    }

    @Test
    @DisplayName("Deve alterar plano para PRO e atualizar cotas de recursos")
    void shouldChangeSubscriptionPlanToPro() throws Exception {
        String email = "upgrade-dono-" + UUID.randomUUID() + "@oficina.com.br";
        RegisterWorkshopRequest reg = new RegisterWorkshopRequest(
                "Oficina Upgrade",
                "Av. Central, 200",
                4,
                List.of("Mecânica Geral"),
                "Dono Upgrade",
                email,
                "SenhaForte@123"
        );

        String regJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AuthResponse auth = objectMapper.readValue(regJson, AuthResponse.class);
        String token = "Bearer " + auth.accessToken();

        // Alterar plano para PRO
        ChangePlanRequest changeReq = new ChangePlanRequest("PRO");
        mockMvc.perform(post("/api/v1/billing/subscription/change-plan")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("PRO"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Deve bloquear criação de unidades além da cota do plano e permitir após upgrade")
    void shouldEnforceUnitsQuotaWhenExceedingLimit() throws Exception {
        String email = "quota-dono-" + UUID.randomUUID() + "@oficina.com.br";
        RegisterWorkshopRequest reg = new RegisterWorkshopRequest(
                "Oficina Quota Test",
                "Rua Teste, 100",
                4,
                List.of("Mecânica Geral"),
                "Dono Quota",
                email,
                "SenhaForte@123"
        );

        String regJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AuthResponse auth = objectMapper.readValue(regJson, AuthResponse.class);
        String token = "Bearer " + auth.accessToken();

        // Matriz já consome 1 unidade. Tentar criar 2ª unidade no plano TRIAL (limite = 1)
        CreateUnitRequest branchReq = new CreateUnitRequest("Filial 2", "Rua Secundária, 200", false);
        mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(branchReq)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.title").value("Quota Exceeded"))
                .andExpect(jsonPath("$.dimension").value("UNITS"));

        // Fazer upgrade para o plano PRO (limite de 3 unidades)
        ChangePlanRequest changeReq = new ChangePlanRequest("PRO");
        mockMvc.perform(post("/api/v1/billing/subscription/change-plan")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("PRO"));

        // Re-tentar criar a filial -> Agora deve ser permitido (200 OK)
        mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(branchReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Filial 2"));
    }

    @Test
    @DisplayName("Deve registrar e consultar consumo de recursos tarifados")
    void shouldRecordAndTrackUsageInCurrentPeriod() throws Exception {
        String email = "usage-dono-" + UUID.randomUUID() + "@oficina.com.br";
        RegisterWorkshopRequest reg = new RegisterWorkshopRequest(
                "Oficina Usage Tracking",
                "Av. das Nações, 500",
                4,
                List.of("Mecânica Geral"),
                "Dono Usage",
                email,
                "SenhaForte@123"
        );

        String regJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AuthResponse auth = objectMapper.readValue(regJson, AuthResponse.class);
        String token = "Bearer " + auth.accessToken();

        // Registrar consumo de IA
        RecordUsageRequest usageReq = new RecordUsageRequest(QuotaDimension.AI_USAGE, 25L, null);
        mockMvc.perform(post("/api/v1/billing/usage/record")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usageReq)))
                .andExpect(status().isAccepted());

        // Consultar consumo
        mockMvc.perform(get("/api/v1/billing/usage")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.dimension == 'AI_USAGE')].amount").value(25));
    }
}
