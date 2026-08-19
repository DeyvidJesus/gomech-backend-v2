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
class OperationsQuoteIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record WorkshopContext(String token, UUID unitId, UUID tenantId) {}

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
        return new WorkshopContext("Bearer " + auth.accessToken(), auth.user().activeUnitId(), auth.user().tenantId());
    }

    @Test
    @DisplayName("Deve criar orçamento com peças e serviços, recalcular totais e consultar detalhes")
    void shouldCreateQuoteWithItemsAndRecalculateTotalsSuccessfully() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-quote-calc");

        // 1. Cadastrar cliente e veículo
        CreateCustomerRequest custReq = new CreateCustomerRequest("Mariana Lima", "529.982.247-25", "(11) 98888-3333", "mariana@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "ABC1D23", "Toyota", "Corolla", 2022, "12345678901234567", 45000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        // 2. Criar orçamento com 1 Peça e 1 Mão de Obra
        SaveQuoteItemRequest partItem = new SaveQuoteItemRequest(
                null,
                QuoteItemType.PART,
                null,
                "Amortecedor Dianteiro",
                "Substituição par dianteiro",
                new BigDecimal("2.00"),
                new BigDecimal("250.00"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO
        ); // Bruto: 500, Desconto: 50, Líquido: 450, Total: 450

        SaveQuoteItemRequest laborItem = new SaveQuoteItemRequest(
                null,
                QuoteItemType.LABOR,
                null,
                "Mão de obra troca amortecedores",
                "Serviço especializado",
                new BigDecimal("1.00"),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                new BigDecimal("10.00")
        ); // Bruto: 150, Desconto: 0, Imposto 10%: 15, Total: 165

        CreateQuoteRequest quoteReq = new CreateQuoteRequest(
                ctx.unitId(),
                customer.id(),
                vehicle.id(),
                null,
                null,
                OffsetDateTime.now().plusDays(15),
                "Orçamento de suspensão",
                "Garantia de 90 dias",
                List.of(partItem, laborItem)
        );

        String quoteJson = mockMvc.perform(post("/api/v1/quotes")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.customerApprovalStatus").value("PENDING"))
                .andExpect(jsonPath("$.subtotalAmount").value(650.00)) // 500 + 150
                .andExpect(jsonPath("$.discountAmount").value(50.00))
                .andExpect(jsonPath("$.taxAmount").value(15.00))
                .andExpect(jsonPath("$.totalPartsAmount").value(450.00))
                .andExpect(jsonPath("$.totalLaborAmount").value(165.00))
                .andExpect(jsonPath("$.totalAmount").value(615.00)) // 650 - 50 + 15
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        QuoteResponse quote = objectMapper.readValue(quoteJson, QuoteResponse.class);

        // 3. Atualizar itens do orçamento (substituindo por novo cálculo)
        SaveQuoteItemRequest newPart = new SaveQuoteItemRequest(
                null,
                QuoteItemType.PART,
                null,
                "Kit Batentes",
                "Troca completa",
                new BigDecimal("1.00"),
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        mockMvc.perform(put("/api/v1/quotes/" + quote.id() + "/items")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(newPart))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotalAmount").value(100.00))
                .andExpect(jsonPath("$.totalAmount").value(100.00))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    @DisplayName("Deve validar o fluxo estrito de dupla aprovação (aprovação interna obrigatória antes do envio ao cliente)")
    void shouldEnforceDualApprovalWorkflow() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-quote-dual-appr");

        CreateCustomerRequest custReq = new CreateCustomerRequest("Carlos Eduardo", null, "(11) 91234-5678", "carlos@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(custReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "KTR1A23", "Ford", "Ka", 2020, "11112222333344445", 20000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(vehReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        CreateQuoteRequest quoteReq = new CreateQuoteRequest(ctx.unitId(), customer.id(), vehicle.id(), null, null, null, null, null, List.of());
        String quoteJson = mockMvc.perform(post("/api/v1/quotes").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(quoteReq))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        QuoteResponse quote = objectMapper.readValue(quoteJson, QuoteResponse.class);

        // 1. Tentar enviar ao cliente direto de DRAFT -> Deve falhar com 422
        mockMvc.perform(post("/api/v1/quotes/" + quote.id() + "/send")
                        .header("Authorization", ctx.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Quote Not Approved For Sending"));

        // 2. Submeter para aprovação interna
        mockMvc.perform(post("/api/v1/quotes/" + quote.id() + "/submit-approval")
                        .header("Authorization", ctx.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_INTERNAL_APPROVAL"));

        // 3. Aprovação interna pelo gerente/admin
        mockMvc.perform(post("/api/v1/quotes/" + quote.id() + "/approve")
                        .header("Authorization", ctx.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERNAL_APPROVED"))
                .andExpect(jsonPath("$.approvedByUserId").isNotEmpty())
                .andExpect(jsonPath("$.approvedAt").isNotEmpty());

        // 4. Agora sim, enviar ao cliente
        mockMvc.perform(post("/api/v1/quotes/" + quote.id() + "/send")
                        .header("Authorization", ctx.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT_TO_CUSTOMER"));

        // 5. Registrar aprovação do cliente
        CustomerDecisionRequest decision = new CustomerDecisionRequest(true, "Aprovado pelo cliente via telefone");
        mockMvc.perform(post("/api/v1/quotes/" + quote.id() + "/customer-decision")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CUSTOMER_APPROVED"))
                .andExpect(jsonPath("$.customerApprovalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.customerDecisionNotes").value("Aprovado pelo cliente via telefone"));

        // 6. Tentar modificar orçamento após aprovação final do cliente -> Deve falhar com 422
        mockMvc.perform(put("/api/v1/quotes/" + quote.id() + "/items")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Quote Cannot Be Modified"));
    }

    @Test
    @DisplayName("Deve gerar orçamento a partir de vistoria técnica com itens em atenção e críticos")
    void shouldGenerateQuoteFromInspection() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-quote-insp");

        CreateCustomerRequest custReq = new CreateCustomerRequest("Juliana Silva", null, "(11) 97777-6666", "juliana@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(custReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "XYZ9K88", "Honda", "Civic", 2021, "98765432109876543", 30000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(vehReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        // Criar inspeção com itens OK, ATTENTION e CRITICAL
        SaveInspectionItemRequest itemOk = new SaveInspectionItemRequest(null, InspectionCategory.FLUIDS, "Nível de Óleo", InspectionItemStatus.OK, "Nível correto", null, null);
        SaveInspectionItemRequest itemAtt = new SaveInspectionItemRequest(null, InspectionCategory.TIRES, "Pneu Dianteiro", InspectionItemStatus.ATTENTION, "Meia vida", "Alinhamento e balanceamento", null);
        SaveInspectionItemRequest itemCrit = new SaveInspectionItemRequest(null, InspectionCategory.BRAKES, "Pastilha de Freio", InspectionItemStatus.CRITICAL, "No ferro", "Substituição urgente das pastilhas", null);

        CreateInspectionRequest inspReq = new CreateInspectionRequest(
                ctx.unitId(),
                customer.id(),
                vehicle.id(),
                null,
                FuelLevel.HALF,
                30000,
                "Vistoria periódica",
                List.of(itemOk, itemAtt, itemCrit)
        );

        String inspJson = mockMvc.perform(post("/api/v1/inspections")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inspReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        InspectionResponse inspection = objectMapper.readValue(inspJson, InspectionResponse.class);

        // Gerar orçamento a partir da vistoria
        mockMvc.perform(post("/api/v1/quotes/from-inspection/" + inspection.id())
                        .header("Authorization", ctx.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inspectionId").value(inspection.id().toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.items", hasSize(2))) // Apenas ATTENTION e CRITICAL
                .andExpect(jsonPath("$.items[?(@.name =~ /.*Pastilha de Freio.*/)]").exists())
                .andExpect(jsonPath("$.items[?(@.name =~ /.*Pneu Dianteiro.*/)]").exists());
    }

    @Test
    @DisplayName("Deve buscar orçamentos paginados e cancelar com sucesso")
    void shouldSearchAndCancelQuote() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-quote-search");

        CreateCustomerRequest custReq = new CreateCustomerRequest("Marcos Paulo", null, "(11) 94444-5555", "marcos@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(custReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "FGH5J67", "VW", "Golf", 2019, "55556666777788889", 60000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(vehReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        CreateQuoteRequest quoteReq = new CreateQuoteRequest(ctx.unitId(), customer.id(), vehicle.id(), null, null, null, "Para cancelamento", null, List.of());
        String quoteJson = mockMvc.perform(post("/api/v1/quotes").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(quoteReq))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        QuoteResponse quote = objectMapper.readValue(quoteJson, QuoteResponse.class);

        // Listar paginado
        mockMvc.perform(get("/api/v1/quotes")
                        .header("Authorization", ctx.token())
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));

        // Cancelar orçamento
        mockMvc.perform(delete("/api/v1/quotes/" + quote.id())
                        .header("Authorization", ctx.token())
                        .param("reason", "Cliente optou por não realizar o serviço"))
                .andExpect(status().isNoContent());

        // Consulta subsequente retorna 404 (soft deleted)
        mockMvc.perform(get("/api/v1/quotes/" + quote.id())
                        .header("Authorization", ctx.token()))
                .andExpect(status().isNotFound());
    }
}
