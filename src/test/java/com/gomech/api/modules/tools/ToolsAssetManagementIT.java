package com.gomech.api.modules.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.billing.domain.PlanCode;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.CreateUnitRequest;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
import com.gomech.api.modules.iam.api.dto.UnitResponse;
import com.gomech.api.modules.tools.api.dto.CreateToolRequest;
import com.gomech.api.modules.tools.api.dto.ToolCategoryRequest;
import com.gomech.api.modules.tools.api.dto.ToolCategoryResponse;
import com.gomech.api.modules.tools.api.dto.ToolCustodyDtos;
import com.gomech.api.modules.tools.api.dto.ToolMaintenanceDtos;
import com.gomech.api.modules.tools.api.dto.ToolResponse;
import com.gomech.api.modules.tools.api.dto.ToolTransferDtos;
import com.gomech.api.modules.tools.api.dto.ToolUsageDtos;
import com.gomech.api.modules.tools.domain.MaintenanceType;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class ToolsAssetManagementIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record WorkshopContext(String token, UUID unitId, UUID tenantId, UUID userId) {}

    private WorkshopContext registerWorkshopAndGetContext(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@oficina.com.br";
        RegisterWorkshopRequest reg = new RegisterWorkshopRequest(
                "Oficina " + prefix,
                "Av. das Nações, 500",
                4,
                List.of("Mecânica Geral", "Injeção Eletrônica"),
                "Proprietário " + prefix,
                email,
                "SenhaForte@123"
        );

        String authJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AuthResponse auth = objectMapper.readValue(authJson, AuthResponse.class);
        return new WorkshopContext(auth.accessToken(), auth.user().activeUnitId(), auth.user().tenantId(), auth.user().id());
    }

    @Test
    @DisplayName("Complete Tools Lifecycle: Category -> Create Tool -> Check-out -> Check-in -> Maintenance -> Transfer")
    void completeToolLifecycleIntegrationTest() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("tools-full");

        // 1. Create Tool Category (Torquímetro com calibração semestral)
        ToolCategoryRequest.Create catReq = ToolCategoryRequest.Create.builder()
                .name("Torquímetros Metrológicos")
                .description("Instrumentos de precisão com aferição periódica")
                .requiresCalibration(true)
                .defaultMaintenanceIntervalDays(180)
                .build();

        String catJson = mockMvc.perform(post("/api/v1/tools/categories")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(catReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Torquímetros Metrológicos")))
                .andExpect(jsonPath("$.requiresCalibration", is(true)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        ToolCategoryResponse category = objectMapper.readValue(catJson, ToolCategoryResponse.class);

        // 2. Create Tool Asset
        CreateToolRequest toolReq = CreateToolRequest.builder()
                .unitId(ctx.unitId())
                .categoryId(category.id())
                .assetTag("TORQ-精密-01")
                .serialNumber("SN-987654321")
                .name("Torquímetro de Vareta e Relógio 1/2")
                .brand("Stahlwille")
                .model("Manoskop 730")
                .locationInUnit("Armário Central - Prateleira A")
                .purchaseDate(LocalDate.now().minusMonths(2))
                .purchaseCost(BigDecimal.valueOf(1450.00))
                .build();

        String toolJson = mockMvc.perform(post("/api/v1/tools")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toolReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assetTag", is("TORQ-精密-01")))
                .andExpect(jsonPath("$.status", is("AVAILABLE")))
                .andExpect(jsonPath("$.categoryName", is("Torquímetros Metrológicos")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        ToolResponse tool = objectMapper.readValue(toolJson, ToolResponse.class);

        // 3. Mechanic Check-out
        ToolCustodyDtos.CheckOut checkOutReq = ToolCustodyDtos.CheckOut.builder()
                .toolId(tool.id())
                .mechanicUserId(ctx.userId())
                .notes("Retirado para aperto de cabeçote")
                .build();

        mockMvc.perform(post("/api/v1/tools/custody/check-out")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkOutReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType", is("CHECK_OUT")))
                .andExpect(jsonPath("$.toUserId", is(ctx.userId().toString())));

        // Verify tool status is now IN_USE
        mockMvc.perform(get("/api/v1/tools/" + tool.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_USE")))
                .andExpect(jsonPath("$.currentHolderUserId", is(ctx.userId().toString())));

        // 4. Return / Check-in Tool
        ToolCustodyDtos.CheckIn checkInReq = ToolCustodyDtos.CheckIn.builder()
                .toolId(tool.id())
                .locationInUnit("Armário Central - Prateleira A")
                .notes("Devolvido limpo e lubrificado")
                .build();

        mockMvc.perform(post("/api/v1/tools/custody/check-in")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkInReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType", is("CHECK_IN")));

        // Verify tool status restored to AVAILABLE
        mockMvc.perform(get("/api/v1/tools/" + tool.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("AVAILABLE")))
                .andExpect(jsonPath("$.currentHolderUserId").doesNotExist());

        // 5. Record Usage on Work Order
        UUID fakeWorkOrderId = UUID.randomUUID();
        ToolUsageDtos.RecordUsage woUsageReq = ToolUsageDtos.RecordUsage.builder()
                .toolId(tool.id())
                .workOrderId(fakeWorkOrderId)
                .mechanicUserId(ctx.userId())
                .notes("Aperto de mancais na OS")
                .build();

        String usageJson = mockMvc.perform(post("/api/v1/tools/usages")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(woUsageReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workOrderId", is(fakeWorkOrderId.toString())))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        ToolUsageDtos.UsageResponse usage = objectMapper.readValue(usageJson, ToolUsageDtos.UsageResponse.class);

        // Finish usage on Work Order
        mockMvc.perform(post("/api/v1/tools/usages/" + usage.id() + "/finish")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .param("notes", "Serviço na OS finalizado com sucesso"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedInAt").exists());

        // 6. Schedule and Complete Maintenance / Calibration
        ToolMaintenanceDtos.Schedule maintReq = ToolMaintenanceDtos.Schedule.builder()
                .toolId(tool.id())
                .maintenanceType(MaintenanceType.CALIBRATION)
                .scheduledDate(LocalDate.now().plusDays(3))
                .performedByProvider("Lab Metrologia Brasil")
                .estimatedCost(BigDecimal.valueOf(320.00))
                .description("Calibração anual acreditada RBC")
                .build();

        String maintJson = mockMvc.perform(post("/api/v1/tools/maintenances/schedule")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maintReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("SCHEDULED")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        ToolMaintenanceDtos.Response maint = objectMapper.readValue(maintJson, ToolMaintenanceDtos.Response.class);

        // Complete Calibration
        ToolMaintenanceDtos.Complete completeMaintReq = ToolMaintenanceDtos.Complete.builder()
                .performedByProvider("Lab Metrologia Brasil")
                .cost(BigDecimal.valueOf(350.00))
                .findings("Certificado nº RBC-998822 emitido. Erro máximo: 0.05 Nm.")
                .nextDueDate(LocalDate.now().plusMonths(6))
                .build();

        mockMvc.perform(post("/api/v1/tools/maintenances/" + maint.id() + "/complete")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeMaintReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.cost", is(350.00)));

        // 7. Inter-Unit Transfer (requires creating 2nd unit in Enterprise plan)
        com.gomech.api.modules.billing.api.dto.ChangePlanRequest upgradeReq = new com.gomech.api.modules.billing.api.dto.ChangePlanRequest("ENTERPRISE");
        mockMvc.perform(post("/api/v1/billing/subscription/change-plan")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isOk());

        CreateUnitRequest unitReq = new CreateUnitRequest("Filial Sul Ferramentas", "Rua das Oficinas 500", false);
        String unitJson = mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unitReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        UnitResponse unit2 = objectMapper.readValue(unitJson, UnitResponse.class);

        // Send Tool to Unit 2
        ToolTransferDtos.Create transferReq = ToolTransferDtos.Create.builder()
                .toolId(tool.id())
                .destinationUnitId(unit2.id())
                .notes("Remessa temporária para atendimento especial")
                .build();

        String transferJson = mockMvc.perform(post("/api/v1/tools/transfers")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferNumber").exists())
                .andExpect(jsonPath("$.status", is("IN_TRANSIT")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        ToolTransferDtos.Response transfer = objectMapper.readValue(transferJson, ToolTransferDtos.Response.class);

        // Complete Transfer at destination
        mockMvc.perform(post("/api/v1/tools/transfers/" + transfer.id() + "/complete")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        // Verify tool now resides in Unit 2
        mockMvc.perform(get("/api/v1/tools/" + tool.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitId", is(unit2.id().toString())))
                .andExpect(jsonPath("$.status", is("AVAILABLE")));

        // 8. Verify complete custody history (2 manual, 2 WO usage, 2 calibration, 2 branch transfer)
        mockMvc.perform(get("/api/v1/tools/custody/history/" + tool.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));
    }

    @Test
    @DisplayName("Tenant Isolation: Workshop B must not access Workshop A tools")
    void tenantIsolationIntegrationTest() throws Exception {
        WorkshopContext ctxA = registerWorkshopAndGetContext("tenant-a-tools");
        WorkshopContext ctxB = registerWorkshopAndGetContext("tenant-b-tools");

        CreateToolRequest toolReq = CreateToolRequest.builder()
                .unitId(ctxA.unitId())
                .assetTag("CHAV-01")
                .name("Chave de Fenda Tenant A")
                .build();

        String toolJson = mockMvc.perform(post("/api/v1/tools")
                        .header("Authorization", "Bearer " + ctxA.token())
                        .header("X-Tenant-ID", ctxA.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toolReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        ToolResponse toolA = objectMapper.readValue(toolJson, ToolResponse.class);

        // Workshop B attempts to access Tool A
        mockMvc.perform(get("/api/v1/tools/" + toolA.id())
                        .header("Authorization", "Bearer " + ctxB.token())
                        .header("X-Tenant-ID", ctxB.tenantId().toString()))
                .andExpect(status().isNotFound());
    }
}
