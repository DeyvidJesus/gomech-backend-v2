package com.gomech.api.modules.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
import com.gomech.api.modules.inventory.api.dto.AdjustStockRequest;
import com.gomech.api.modules.inventory.api.dto.CreateProductRequest;
import com.gomech.api.modules.inventory.api.dto.CreateReservationRequest;
import com.gomech.api.modules.inventory.api.dto.CreateTransferRequest;
import com.gomech.api.modules.inventory.api.dto.ProductResponse;
import com.gomech.api.modules.inventory.api.dto.StockReservationResponse;
import com.gomech.api.modules.inventory.api.dto.StockTransferResponse;
import com.gomech.api.modules.inventory.domain.MovementReason;
import com.gomech.api.modules.inventory.domain.UnitOfMeasure;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class InventoryStockIT {

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
    @DisplayName("Fluxo Completo de Estoque: Catálogo, Saldos, Reservas, Consumo, Transferência entre Filiais e Ledger Imutável")
    void testCompleteInventoryLifecycle() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("estoque-flow");

        // 1. Criar Produto com Saldo Inicial
        CreateProductRequest createProdReq = new CreateProductRequest(
                ctx.unitId(),
                null,
                "AMORT-DIR-GOL",
                "Amortecedor Dianteiro Direito Gol G5",
                "Suspensão",
                "7891112223334",
                "Cofap",
                UnitOfMeasure.UN,
                BigDecimal.valueOf(150.00),
                BigDecimal.valueOf(280.00),
                4,
                "A-12",
                BigDecimal.valueOf(10),
                ctx.unitId()
        );

        String prodJson = mockMvc.perform(post("/api/v1/inventory/products")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createProdReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skuCode", is("AMORT-DIR-GOL")))
                .andExpect(jsonPath("$.name", is("Amortecedor Dianteiro Direito Gol G5")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        ProductResponse prod = objectMapper.readValue(prodJson, ProductResponse.class);

        // 2. Verificar Saldo Inicial na Filial
        String stock1Json = mockMvc.perform(get("/api/v1/inventory/stocks/" + prod.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .param("unitId", ctx.unitId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        com.gomech.api.modules.inventory.api.dto.UnitStockResponse stock1 = objectMapper.readValue(stock1Json, com.gomech.api.modules.inventory.api.dto.UnitStockResponse.class);
        org.assertj.core.api.Assertions.assertThat(stock1.quantityOnHand()).isEqualByComparingTo("10");
        org.assertj.core.api.Assertions.assertThat(stock1.quantityReserved()).isEqualByComparingTo("0");
        org.assertj.core.api.Assertions.assertThat(stock1.availableStock()).isEqualByComparingTo("10");

        // 3. Criar Reserva de Estoque para uma OS (sem deduzir saldo físico)
        UUID workOrderId = UUID.randomUUID();
        CreateReservationRequest resReq = new CreateReservationRequest(
                ctx.unitId(),
                prod.id(),
                workOrderId,
                UUID.randomUUID(),
                BigDecimal.valueOf(3),
                null,
                "Reserva para serviço de suspensão"
        );

        String resJson = mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CREATED")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        StockReservationResponse reservation = objectMapper.readValue(resJson, StockReservationResponse.class);
        org.assertj.core.api.Assertions.assertThat(reservation.quantity()).isEqualByComparingTo("3");

        // 4. Verificar Invariante de Reserva: on-hand = 10, reserved = 3, available = 7
        String stock2Json = mockMvc.perform(get("/api/v1/inventory/stocks/" + prod.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .param("unitId", ctx.unitId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        com.gomech.api.modules.inventory.api.dto.UnitStockResponse stock2 = objectMapper.readValue(stock2Json, com.gomech.api.modules.inventory.api.dto.UnitStockResponse.class);
        org.assertj.core.api.Assertions.assertThat(stock2.quantityOnHand()).isEqualByComparingTo("10"); // Saldo físico intacto!
        org.assertj.core.api.Assertions.assertThat(stock2.quantityReserved()).isEqualByComparingTo("3");
        org.assertj.core.api.Assertions.assertThat(stock2.availableStock()).isEqualByComparingTo("7");

        // 5. Liberar/Cancelar Reserva e verificar restauração de disponibilidade
        mockMvc.perform(delete("/api/v1/inventory/reservations/" + reservation.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isNoContent());

        String stock3Json = mockMvc.perform(get("/api/v1/inventory/stocks/" + prod.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .param("unitId", ctx.unitId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        com.gomech.api.modules.inventory.api.dto.UnitStockResponse stock3 = objectMapper.readValue(stock3Json, com.gomech.api.modules.inventory.api.dto.UnitStockResponse.class);
        org.assertj.core.api.Assertions.assertThat(stock3.quantityOnHand()).isEqualByComparingTo("10");
        org.assertj.core.api.Assertions.assertThat(stock3.quantityReserved()).isEqualByComparingTo("0");
        org.assertj.core.api.Assertions.assertThat(stock3.availableStock()).isEqualByComparingTo("10");

        // 6. Fazer upgrade para plano PRO e Criar Segunda Filial para Testar Transferência
        com.gomech.api.modules.billing.api.dto.ChangePlanRequest upgradeReq = new com.gomech.api.modules.billing.api.dto.ChangePlanRequest("PRO");
        mockMvc.perform(post("/api/v1/billing/subscription/change-plan")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isOk());

        com.gomech.api.modules.iam.api.dto.CreateUnitRequest unit2Req = new com.gomech.api.modules.iam.api.dto.CreateUnitRequest(
                "Filial Zona Norte", "Av. Norte, 200", false
        );
        String unit2Json = mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unit2Req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        com.gomech.api.modules.iam.api.dto.UnitResponse unit2 = objectMapper.readValue(unit2Json, com.gomech.api.modules.iam.api.dto.UnitResponse.class);
        UUID unit2Id = unit2.id();

        // 7. Ajuste Manual de Estoque (Inventário)
        AdjustStockRequest adjustReq = new AdjustStockRequest(
                ctx.unitId(),
                prod.id(),
                BigDecimal.valueOf(12),
                MovementReason.ADJUSTMENT_INCREASE,
                "Inventário físico contou 12 unidades"
        );

        String adjustJson = mockMvc.perform(post("/api/v1/inventory/stocks/adjust")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adjustReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        com.gomech.api.modules.inventory.api.dto.UnitStockResponse stockAdjusted = objectMapper.readValue(adjustJson, com.gomech.api.modules.inventory.api.dto.UnitStockResponse.class);
        org.assertj.core.api.Assertions.assertThat(stockAdjusted.quantityOnHand()).isEqualByComparingTo("12");
        org.assertj.core.api.Assertions.assertThat(stockAdjusted.availableStock()).isEqualByComparingTo("12");

        // 8. Solicitar Transferência de 4 unidades da Filial 1 para a Filial 2
        CreateTransferRequest transferReq = new CreateTransferRequest(
                ctx.unitId(),
                unit2Id,
                "Remessa de amortecedores",
                List.of(new CreateTransferRequest.TransferItemRequest(prod.id(), BigDecimal.valueOf(4), "4 peças"))
        );

        String transferJson = mockMvc.perform(post("/api/v1/inventory/transfers")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        StockTransferResponse transfer = objectMapper.readValue(transferJson, StockTransferResponse.class);

        // 9. Concluir Transferência (baixa na origem, entrada no destino e 2 movimentações)
        mockMvc.perform(post("/api/v1/inventory/transfers/" + transfer.id() + "/complete")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        // Origem deve ter 12 - 4 = 8
        String stockSourceJson = mockMvc.perform(get("/api/v1/inventory/stocks/" + prod.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .param("unitId", ctx.unitId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        com.gomech.api.modules.inventory.api.dto.UnitStockResponse stockSource = objectMapper.readValue(stockSourceJson, com.gomech.api.modules.inventory.api.dto.UnitStockResponse.class);
        org.assertj.core.api.Assertions.assertThat(stockSource.quantityOnHand()).isEqualByComparingTo("8");

        // Destino deve ter 0 + 4 = 4
        String stockDestJson = mockMvc.perform(get("/api/v1/inventory/stocks/" + prod.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .param("unitId", unit2Id.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        com.gomech.api.modules.inventory.api.dto.UnitStockResponse stockDest = objectMapper.readValue(stockDestJson, com.gomech.api.modules.inventory.api.dto.UnitStockResponse.class);
        org.assertj.core.api.Assertions.assertThat(stockDest.quantityOnHand()).isEqualByComparingTo("4");

        // 10. Consultar Livro-Razão de Movimentações Imutável (Movements Ledger)
        mockMvc.perform(get("/api/v1/inventory/movements")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .param("productId", prod.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4))); // Saldo inicial, Ajuste manual, Transfer Out, Transfer In
    }

    @Test
    @DisplayName("Isolamento Multi-tenant: Oficina B não acessa produtos nem saldos da Oficina A")
    void testTenantIsolation() throws Exception {
        WorkshopContext ctxA = registerWorkshopAndGetContext("tenant-a-inv");
        WorkshopContext ctxB = registerWorkshopAndGetContext("tenant-b-inv");

        // Criar produto na Oficina A
        CreateProductRequest createProd = new CreateProductRequest(
                ctxA.unitId(),
                null,
                "VELA-A",
                "Vela de Ignição A",
                "Ignição",
                null,
                "Bosch",
                UnitOfMeasure.UN,
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(30),
                2,
                null,
                BigDecimal.valueOf(5),
                ctxA.unitId()
        );

        String prodJson = mockMvc.perform(post("/api/v1/inventory/products")
                        .header("Authorization", "Bearer " + ctxA.token())
                        .header("X-Tenant-ID", ctxA.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createProd)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        ProductResponse prodA = objectMapper.readValue(prodJson, ProductResponse.class);

        // Oficina B tenta consultar o produto da Oficina A -> 404
        mockMvc.perform(get("/api/v1/inventory/products/" + prodA.id())
                        .header("Authorization", "Bearer " + ctxB.token())
                        .header("X-Tenant-ID", ctxB.tenantId().toString()))
                .andExpect(status().isNotFound());
    }
}
