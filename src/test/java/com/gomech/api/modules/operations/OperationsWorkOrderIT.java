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
class OperationsWorkOrderIT {

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
    @DisplayName("Deve criar ordem de serviço com peças e serviços, atualizar dados e itens e recalcular totais")
    void shouldCreateWorkOrderWithItemsAndRecalculateTotalsSuccessfully() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-wo-crud");

        // 1. Cadastrar cliente e veículo
        CreateCustomerRequest custReq = new CreateCustomerRequest("Bruno Henrique", null, "(11) 98888-7777", "bruno@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "ABC1D23", "Toyota", "Corolla", 2022, "9BWZZZ377VT004251", 45000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        // 2. Criar ordem de serviço
        SaveWorkOrderItemRequest part1 = new SaveWorkOrderItemRequest(
                null,
                WorkOrderItemType.PART,
                null,
                null,
                "Jogo de Velas de Ignição",
                "Peça original",
                WorkOrderItemStatus.PENDING,
                new BigDecimal("1.00"),
                new BigDecimal("220.00"),
                new BigDecimal("20.00"),
                BigDecimal.ZERO
        ); // 200.00

        SaveWorkOrderItemRequest service1 = new SaveWorkOrderItemRequest(
                null,
                WorkOrderItemType.SERVICE,
                null,
                ctx.userId(),
                "Substituição das velas",
                "Mão de obra",
                WorkOrderItemStatus.PENDING,
                new BigDecimal("1.00"),
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("5.00")
        ); // 105.00

        CreateWorkOrderRequest woReq = new CreateWorkOrderRequest(
                ctx.unitId(),
                customer.id(),
                vehicle.id(),
                null,
                ctx.userId(),
                "Box 03",
                45000,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusHours(3),
                "Verificar falhas na aceleração",
                "Possível vela carbonizada",
                "Cliente aguardando",
                List.of(part1, service1)
        );

        String woJson = mockMvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(woReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber", notNullValue()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.serviceBay").value("Box 03"))
                .andExpect(jsonPath("$.totalPartsAmount").value(200.00))
                .andExpect(jsonPath("$.totalServicesAmount").value(105.00))
                .andExpect(jsonPath("$.totalAmount").value(305.00))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        WorkOrderResponse createdWo = objectMapper.readValue(woJson, WorkOrderResponse.class);

        // 3. Atualizar dados gerais (box e notas)
        UpdateWorkOrderRequest updateReq = new UpdateWorkOrderRequest(
                ctx.userId(),
                "Box 04",
                45010,
                null,
                null,
                "Velas trocadas com sucesso",
                "Diagnóstico confirmado",
                null
        );

        mockMvc.perform(put("/api/v1/work-orders/" + createdWo.id())
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceBay").value("Box 04"))
                .andExpect(jsonPath("$.startMileage").value(45010));

        // 4. Atualizar itens dinamicamente
        SaveWorkOrderItemRequest partExtra = new SaveWorkOrderItemRequest(
                null,
                WorkOrderItemType.PART,
                null,
                null,
                "Cabo de Vela",
                "Substituição preventiva",
                WorkOrderItemStatus.PENDING,
                new BigDecimal("1.00"),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        mockMvc.perform(put("/api/v1/work-orders/" + createdWo.id() + "/items")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(part1, service1, partExtra))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.totalPartsAmount").value(350.00)) // 200 + 150
                .andExpect(jsonPath("$.totalServicesAmount").value(105.00))
                .andExpect(jsonPath("$.totalAmount").value(455.00));
    }

    @Test
    @DisplayName("Deve converter orçamento aprovado em ordem de serviço atomicamente e prevenir conversão duplicada")
    void shouldConvertApprovedQuoteIntoWorkOrderAtomically() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-wo-quote-conv");

        // 1. Cadastrar cliente e veículo
        CreateCustomerRequest custReq = new CreateCustomerRequest("Mariana Lima", null, "(11) 96666-5555", "mariana@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(custReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "BRA2E19", "Fiat", "Pulse", 2023, "9BD12345678901234", 15000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(vehReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        // 2. Criar orçamento
        SaveQuoteItemRequest item1 = new SaveQuoteItemRequest(
                null,
                QuoteItemType.PART,
                null,
                "Óleo Sintético 0W20",
                "4 Litros",
                new BigDecimal("4.00"),
                new BigDecimal("60.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        ); // 240.00

        CreateQuoteRequest quoteReq = new CreateQuoteRequest(
                ctx.unitId(),
                customer.id(),
                vehicle.id(),
                null,
                null,
                OffsetDateTime.now().plusDays(7),
                "Revisão dos 15.000 km",
                "Garantia de 90 dias",
                List.of(item1)
        );

        String quoteJson = mockMvc.perform(post("/api/v1/quotes")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        QuoteResponse quote = objectMapper.readValue(quoteJson, QuoteResponse.class);

        // 3. Tentar converter antes da aprovação -> 422
        mockMvc.perform(post("/api/v1/work-orders/from-quote/" + quote.id())
                        .header("Authorization", ctx.token()))
                .andExpect(status().isUnprocessableEntity());

        // 4. Executar fluxo de dupla aprovação no orçamento
        mockMvc.perform(post("/api/v1/quotes/" + quote.id() + "/submit-approval").header("Authorization", ctx.token())).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/quotes/" + quote.id() + "/approve").header("Authorization", ctx.token())).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/quotes/" + quote.id() + "/send").header("Authorization", ctx.token())).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/quotes/" + quote.id() + "/customer-decision")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CustomerDecisionRequest(true, "Aprovado via WhatsApp"))))
                .andExpect(status().isOk());

        // 5. Converter orçamento aprovado em ordem de serviço -> 201 Created
        String woJson = mockMvc.perform(post("/api/v1/work-orders/from-quote/" + quote.id())
                        .header("Authorization", ctx.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quoteId").value(quote.id().toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.totalAmount").value(240.00))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("PART"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 6. Tentar converter o mesmo orçamento novamente -> 422 Unprocessable Entity
        mockMvc.perform(post("/api/v1/work-orders/from-quote/" + quote.id())
                        .header("Authorization", ctx.token()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Deve transicionar status operacional, finalizar OS e bloquear modificações pós-finalização")
    void shouldTransitionWorkOrderStatusAndCompleteSuccessfully() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-wo-lifecycle");

        CreateCustomerRequest custReq = new CreateCustomerRequest("Fernanda Souza", null, "(11) 95555-4444", "fernanda@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(custReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "KMN9J88", "Jeep", "Renegade", 2021, "9BD12345678909876", 50000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(vehReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        SaveWorkOrderItemRequest service1 = new SaveWorkOrderItemRequest(
                null,
                WorkOrderItemType.SERVICE,
                null,
                ctx.userId(),
                "Alinhamento 3D e Balanceamento",
                "Serviço completo",
                WorkOrderItemStatus.PENDING,
                new BigDecimal("1.00"),
                new BigDecimal("180.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        CreateWorkOrderRequest woReq = new CreateWorkOrderRequest(
                ctx.unitId(), customer.id(), vehicle.id(), null, ctx.userId(), "Box 02", 50000,
                OffsetDateTime.now(), null, null, null, null, List.of(service1)
        );

        String woJson = mockMvc.perform(post("/api/v1/work-orders").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(woReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        WorkOrderResponse wo = objectMapper.readValue(woJson, WorkOrderResponse.class);

        // 1. OPEN -> IN_PROGRESS
        mockMvc.perform(put("/api/v1/work-orders/" + wo.id() + "/status")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeWorkOrderStatusRequest(WorkOrderStatus.IN_PROGRESS, "Iniciando geometria"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startDate", notNullValue()));

        // 2. IN_PROGRESS -> WAITING_PARTS
        mockMvc.perform(put("/api/v1/work-orders/" + wo.id() + "/status")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeWorkOrderStatusRequest(WorkOrderStatus.WAITING_PARTS, "Aguardando parafuso especial"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_PARTS"));

        // 3. WAITING_PARTS -> IN_PROGRESS
        mockMvc.perform(put("/api/v1/work-orders/" + wo.id() + "/status")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeWorkOrderStatusRequest(WorkOrderStatus.IN_PROGRESS, "Peça recebida, retomando"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // 4. Finalizar OS com quilometragem final
        CompleteWorkOrderRequest completeReq = new CompleteWorkOrderRequest(
                50015,
                "Alinhamento concluído dentro das tolerâncias do fabricante.",
                "Veículo testado e liberado."
        );

        mockMvc.perform(post("/api/v1/work-orders/" + wo.id() + "/complete")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt", notNullValue()))
                .andExpect(jsonPath("$.endMileage").value(50015))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"));

        // 5. Tentar modificar itens após finalização -> 422 Unprocessable Entity
        mockMvc.perform(put("/api/v1/work-orders/" + wo.id() + "/items")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(service1))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Deve consultar o quadro Kanban agrupando ordens ativas por status")
    void shouldQueryKanbanBoardSuccessfully() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-wo-kanban");

        CreateCustomerRequest custReq = new CreateCustomerRequest("Lucas Mendes", null, "(11) 94444-3333", "lucas@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(custReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "LMN4K55", "Chevrolet", "Onix", 2022, "9BG12345678901234", 30000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(vehReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        // Criar 1 OS em OPEN
        CreateWorkOrderRequest woReq1 = new CreateWorkOrderRequest(
                ctx.unitId(), customer.id(), vehicle.id(), null, ctx.userId(), "Box 01", 30000,
                null, null, null, null, null, List.of()
        );
        mockMvc.perform(post("/api/v1/work-orders").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(woReq1))).andExpect(status().isCreated());

        // Consultar Kanban
        mockMvc.perform(get("/api/v1/work-orders/kanban?unitId=" + ctx.unitId())
                        .header("Authorization", ctx.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitId").value(ctx.unitId().toString()))
                .andExpect(jsonPath("$.columns", hasSize(4)))
                .andExpect(jsonPath("$.columns[0].status").value("OPEN"))
                .andExpect(jsonPath("$.columns[0].totalOrders", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.columns[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.columns[2].status").value("WAITING_PARTS"))
                .andExpect(jsonPath("$.columns[3].status").value("WAITING_CUSTOMER"));
    }

    @Test
    @DisplayName("Deve cancelar ordem de serviço e aplicar soft delete")
    void shouldCancelWorkOrderWithSoftDelete() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-wo-cancel");

        CreateCustomerRequest custReq = new CreateCustomerRequest("Roberto Dias", null, "(11) 93333-2222", "roberto@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(custReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "RST7H88", "Toyota", "Yaris", 2021, "9BR12345678901234", 25000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(vehReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        CreateWorkOrderRequest woReq = new CreateWorkOrderRequest(
                ctx.unitId(), customer.id(), vehicle.id(), null, null, null, null,
                null, null, null, null, null, List.of()
        );
        String woJson = mockMvc.perform(post("/api/v1/work-orders").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(woReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        WorkOrderResponse wo = objectMapper.readValue(woJson, WorkOrderResponse.class);

        // Cancelar OS -> 204 No Content
        mockMvc.perform(delete("/api/v1/work-orders/" + wo.id() + "?reason=Cliente+desistiu")
                        .header("Authorization", ctx.token()))
                .andExpect(status().isNoContent());

        // Consultar por ID -> 404 Not Found (soft deleted)
        mockMvc.perform(get("/api/v1/work-orders/" + wo.id())
                        .header("Authorization", ctx.token()))
                .andExpect(status().isNotFound());
    }
}
