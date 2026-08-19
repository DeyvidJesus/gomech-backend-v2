package com.gomech.api.modules.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.crm.api.dto.CreateCustomerRequest;
import com.gomech.api.modules.crm.api.dto.CreateVehicleRequest;
import com.gomech.api.modules.crm.api.dto.CustomerResponse;
import com.gomech.api.modules.crm.api.dto.VehicleResponse;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
import com.gomech.api.modules.operations.api.dto.*;
import com.gomech.api.modules.operations.domain.*;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class OperationsVehicleHistoryIT {

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
                "Av. Principal, 100",
                4,
                List.of("Mecânica Geral"),
                "Dono " + prefix,
                email,
                "SenhaForte@123"
        );

        String regJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AuthResponse auth = objectMapper.readValue(regJson, AuthResponse.class);
        return new WorkshopContext("Bearer " + auth.accessToken(), auth.user().activeUnitId(), auth.user().tenantId(), auth.user().id());
    }

    @Test
    @DisplayName("Deve agregar histórico completo de manutenções, calcular métricas e gerar dossiê exportável")
    void shouldAggregateVehicleServiceHistoryAndGenerateExportSuccessfully() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-veh-hist");

        // 1. Cadastrar cliente e veículo
        CreateCustomerRequest custReq = new CreateCustomerRequest("Juliana Paes", null, "(11) 97777-6666", "juliana@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(custReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "JPA1E23", "Jeep", "Compass", 2022, "9BD12345678909999", 30000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(vehReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        // 2. Criar e concluir OS 1: Troca de óleo (1 peça de 120 + 1 serviço de 80 = 200)
        SaveWorkOrderItemRequest wo1Part = new SaveWorkOrderItemRequest(null, WorkOrderItemType.PART, null, null, "Óleo 5W30 Sintético", "4 Litros", WorkOrderItemStatus.PENDING, new BigDecimal("1.00"), new BigDecimal("120.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        SaveWorkOrderItemRequest wo1Serv = new SaveWorkOrderItemRequest(null, WorkOrderItemType.SERVICE, null, ctx.userId(), "Troca de Óleo e Filtro", "Mão de obra", WorkOrderItemStatus.PENDING, new BigDecimal("1.00"), new BigDecimal("80.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        CreateWorkOrderRequest wo1Req = new CreateWorkOrderRequest(ctx.unitId(), customer.id(), vehicle.id(), null, ctx.userId(), "Box 01", 30000, null, null, null, null, null, List.of(wo1Part, wo1Serv));

        String wo1Json = mockMvc.perform(post("/api/v1/work-orders").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(wo1Req))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        WorkOrderResponse wo1 = objectMapper.readValue(wo1Json, WorkOrderResponse.class);

        mockMvc.perform(put("/api/v1/work-orders/" + wo1.id() + "/status").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new ChangeWorkOrderStatusRequest(WorkOrderStatus.IN_PROGRESS, null)))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/work-orders/" + wo1.id() + "/complete").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new CompleteWorkOrderRequest(30010, "Óleo trocado", "OK")))).andExpect(status().isOk());

        // 3. Criar e concluir OS 2: Freios (2 pastilhas de 150 = 300 + 1 serviço de 150 = 450)
        SaveWorkOrderItemRequest wo2Part = new SaveWorkOrderItemRequest(null, WorkOrderItemType.PART, null, null, "Pastilha Dianteira", "Cerâmica", WorkOrderItemStatus.PENDING, new BigDecimal("2.00"), new BigDecimal("150.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        SaveWorkOrderItemRequest wo2Serv = new SaveWorkOrderItemRequest(null, WorkOrderItemType.SERVICE, null, ctx.userId(), "Substituição de Pastilhas", "Mão de obra", WorkOrderItemStatus.PENDING, new BigDecimal("1.00"), new BigDecimal("150.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        CreateWorkOrderRequest wo2Req = new CreateWorkOrderRequest(ctx.unitId(), customer.id(), vehicle.id(), null, ctx.userId(), "Box 02", 35000, null, null, null, null, null, List.of(wo2Part, wo2Serv));

        String wo2Json = mockMvc.perform(post("/api/v1/work-orders").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(wo2Req))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        WorkOrderResponse wo2 = objectMapper.readValue(wo2Json, WorkOrderResponse.class);

        mockMvc.perform(put("/api/v1/work-orders/" + wo2.id() + "/status").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new ChangeWorkOrderStatusRequest(WorkOrderStatus.IN_PROGRESS, null)))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/work-orders/" + wo2.id() + "/complete").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new CompleteWorkOrderRequest(35015, "Freios revisados", "OK")))).andExpect(status().isOk());

        // 4. Criar OS 3 em Aberto (NÃO deve constar no histórico de finalizadas)
        CreateWorkOrderRequest wo3Req = new CreateWorkOrderRequest(ctx.unitId(), customer.id(), vehicle.id(), null, ctx.userId(), "Box 03", 40000, null, null, null, null, null, List.of());
        mockMvc.perform(post("/api/v1/work-orders").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(wo3Req))).andExpect(status().isCreated());

        // 5. Consultar Histórico do Veículo -> 200 OK
        mockMvc.perform(get("/api/v1/operations/vehicles/" + vehicle.id() + "/history")
                        .header("Authorization", ctx.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value(vehicle.id().toString()))
                .andExpect(jsonPath("$.licensePlate").value("JPA1E23"))
                .andExpect(jsonPath("$.customer.name").value("Juliana Paes"))
                .andExpect(jsonPath("$.metrics.totalServicesCount").value(2))
                .andExpect(jsonPath("$.metrics.totalSpent").value(650.00)) // 200 + 450
                .andExpect(jsonPath("$.metrics.averageTicket").value(325.00)) // 650 / 2
                .andExpect(jsonPath("$.metrics.lastRecordedMileage").value(35015))
                .andExpect(jsonPath("$.metrics.totalPartsReplacedCount").value(3)) // 1 + 2
                .andExpect(jsonPath("$.workOrders", hasSize(2)))
                .andExpect(jsonPath("$.workOrders[0].items", hasSize(2)));

        // 6. Consultar Dossiê Exportável -> 200 OK
        mockMvc.perform(get("/api/v1/operations/vehicles/" + vehicle.id() + "/history/export")
                        .header("Authorization", ctx.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId", startsWith("DOSSIER-JPA1E23-")))
                .andExpect(jsonPath("$.authenticityVerificationCode", startsWith("GM-AUTH-")))
                .andExpect(jsonPath("$.termsAndWarrantyNotice", notNullValue()))
                .andExpect(jsonPath("$.metrics.totalServicesCount").value(2))
                .andExpect(jsonPath("$.completedWorkOrders", hasSize(2)));
    }

    @Test
    @DisplayName("Deve rejeitar consulta de histórico para veículo inexistente ou de outro tenant")
    void shouldRejectHistoryForNonExistentOrCrossTenantVehicle() throws Exception {
        WorkshopContext ctx1 = registerWorkshopAndGetContext("ops-veh-t1");
        WorkshopContext ctx2 = registerWorkshopAndGetContext("ops-veh-t2");

        CreateCustomerRequest custReq = new CreateCustomerRequest("Marcos Paulo", null, "(11) 98888-9999", "marcos@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers").header("Authorization", ctx1.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(custReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "MPT9L00", "Ford", "Ka", 2020, "9BF12345678901234", 60000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles").header("Authorization", ctx1.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(vehReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        // Workshop 2 consultando veículo do Workshop 1 -> 404 Not Found
        mockMvc.perform(get("/api/v1/operations/vehicles/" + vehicle.id() + "/history")
                        .header("Authorization", ctx2.token()))
                .andExpect(status().isNotFound());

        // ID aleatório -> 404 Not Found
        mockMvc.perform(get("/api/v1/operations/vehicles/" + UUID.randomUUID() + "/history")
                        .header("Authorization", ctx1.token()))
                .andExpect(status().isNotFound());
    }
}
