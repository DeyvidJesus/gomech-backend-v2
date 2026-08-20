package com.gomech.api.modules.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.finance.api.dto.AccountDtos;
import com.gomech.api.modules.finance.api.dto.PayableDtos;
import com.gomech.api.modules.finance.api.dto.ReceivableDtos;
import com.gomech.api.modules.finance.application.FinanceEventListener;
import com.gomech.api.modules.finance.domain.AccountType;
import com.gomech.api.modules.finance.domain.PayableStatus;
import com.gomech.api.modules.finance.domain.ReceivableStatus;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
import com.gomech.api.modules.inventory.events.InventoryPurchaseCreatedEvent;
import com.gomech.api.modules.operations.events.WorkOrderCompletedEvent;
import com.gomech.api.modules.operations.events.WorkOrderReopenedEvent;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class FinanceEventDrivenIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FinanceEventListener financeEventListener;

    private record WorkshopContext(String token, UUID unitId, UUID tenantId, UUID userId) {}

    private WorkshopContext registerWorkshopAndGetContext(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@oficina.com.br";
        RegisterWorkshopRequest reg = new RegisterWorkshopRequest(
                "Oficina " + prefix,
                "Av. Brasil, 1500",
                4,
                List.of("Mecânica Geral", "Câmbio Automático"),
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
    @DisplayName("Complete Event-Driven Finance Flow: Events -> Receivables/Payables -> Settlement -> Cash Flow & DRE -> Reversal")
    void completeEventDrivenFinanceIntegrationTest() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("finance-flow");

        // 1. Create Bank Account
        AccountDtos.Create accReq = AccountDtos.Create.builder()
                .unitId(ctx.unitId())
                .name("Banco Itaú Principal")
                .type(AccountType.BANK_ACCOUNT)
                .bankName("Itaú Unibanco")
                .accountNumber("12345-6")
                .agency("0001")
                .initialBalance(BigDecimal.valueOf(5000.00))
                .build();

        String accJson = mockMvc.perform(post("/api/v1/finance/accounts")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Banco Itaú Principal")))
                .andExpect(jsonPath("$.currentBalance", is(5000.00)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AccountDtos.Response account = objectMapper.readValue(accJson, AccountDtos.Response.class);

        // 2. Publish WorkOrderCompletedEvent
        UUID workOrderId = UUID.randomUUID();
        WorkOrderCompletedEvent woCompletedEvent = new WorkOrderCompletedEvent(
                workOrderId,
                ctx.tenantId(),
                ctx.unitId(),
                "OS-2026-0099",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ctx.userId(),
                BigDecimal.valueOf(1800.00),
                BigDecimal.valueOf(800.00),
                BigDecimal.valueOf(1000.00),
                OffsetDateTime.now(),
                45000,
                2,
                OffsetDateTime.now()
        );

        financeEventListener.onWorkOrderCompleted(woCompletedEvent);

        // Verify Receivable created
        String recsJson = mockMvc.perform(get("/api/v1/finance/receivables")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount", is(1800.00)))
                .andExpect(jsonPath("$.content[0].status", is("PENDING")))
                .andExpect(jsonPath("$.content[0].orderNumber", is("OS-2026-0099")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        ReceivableDtos.Response receivable = objectMapper.readTree(recsJson)
                .get("content").get(0).traverse(objectMapper).readValueAs(ReceivableDtos.Response.class);

        // 3. Replay WorkOrderCompletedEvent to test Idempotency
        financeEventListener.onWorkOrderCompleted(woCompletedEvent);

        mockMvc.perform(get("/api/v1/finance/receivables")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1))); // Still exactly 1 receivable

        // 4. Settle Receivable with payment into the account
        ReceivableDtos.Settle settleReq = ReceivableDtos.Settle.builder()
                .accountId(account.id())
                .paidAmount(BigDecimal.valueOf(1800.00))
                .paymentMethod("PIX")
                .notes("Recebido via chave PIX CNPJ")
                .build();

        mockMvc.perform(post("/api/v1/finance/receivables/" + receivable.id() + "/settle")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RECEIVED")))
                .andExpect(jsonPath("$.paidAmount", is(1800.00)));

        // Verify account balance increased (5000 + 1800 = 6800)
        mockMvc.perform(get("/api/v1/finance/accounts/" + account.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance", is(6800.00)));

        // 5. Consume InventoryPurchaseCreatedEvent into Payable
        UUID purchaseId = UUID.randomUUID();
        InventoryPurchaseCreatedEvent purchaseEvent = new InventoryPurchaseCreatedEvent(
                purchaseId,
                ctx.tenantId(),
                ctx.unitId(),
                "Distribuidora de Peças Brasil Ltda",
                "NF-445566",
                BigDecimal.valueOf(600.00),
                LocalDate.now().plusDays(15),
                "Filtros de óleo e fluido DOT4"
        );

        financeEventListener.onInventoryPurchaseCreated(purchaseEvent);

        String paysJson = mockMvc.perform(get("/api/v1/finance/payables")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount", is(600.00)))
                .andExpect(jsonPath("$.content[0].status", is("PENDING")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        PayableDtos.Response payable = objectMapper.readTree(paysJson)
                .get("content").get(0).traverse(objectMapper).readValueAs(PayableDtos.Response.class);

        // 6. Settle Payable
        PayableDtos.Settle settlePayReq = PayableDtos.Settle.builder()
                .accountId(account.id())
                .paidAmount(BigDecimal.valueOf(600.00))
                .paymentMethod("TRANSFERENCIA")
                .notes("TED Fornecedor")
                .build();

        mockMvc.perform(post("/api/v1/finance/payables/" + payable.id() + "/settle")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settlePayReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAID")));

        // Verify account balance updated (6800 - 600 = 6200)
        mockMvc.perform(get("/api/v1/finance/accounts/" + account.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance", is(6200.00)));

        // 7. Verify Cash Flow and DRE Reports
        mockMvc.perform(get("/api/v1/finance/cash-flow")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInflows", is(1800.00)))
                .andExpect(jsonPath("$.totalOutflows", is(600.00)))
                .andExpect(jsonPath("$.netCashFlow", is(1200.00)))
                .andExpect(jsonPath("$.finalBalance", is(6200.00)));

        mockMvc.perform(get("/api/v1/finance/dre")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossRevenue", is(1800.00)))
                .andExpect(jsonPath("$.variableCosts", is(600.00)))
                .andExpect(jsonPath("$.grossProfit", is(1200.00)))
                .andExpect(jsonPath("$.netProfit", is(1200.00)));

        // 8. Reversal: Work Order is Reopened -> Compensating reversal
        WorkOrderReopenedEvent reopenEvent = new WorkOrderReopenedEvent(workOrderId, ctx.tenantId(), ctx.unitId(), "Retorno do cliente para revisão");
        financeEventListener.onWorkOrderReopened(reopenEvent);

        // Verify Receivable is REVERSED and account was debited (6200 - 1800 = 4400)
        mockMvc.perform(get("/api/v1/finance/receivables/" + receivable.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REVERSED")));

        mockMvc.perform(get("/api/v1/finance/accounts/" + account.id())
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance", is(4400.00)));
    }

    @Test
    @DisplayName("Tenant Isolation: Workshop B must not access Workshop A financial records")
    void tenantIsolationIntegrationTest() throws Exception {
        WorkshopContext ctxA = registerWorkshopAndGetContext("tenant-a-fin");
        WorkshopContext ctxB = registerWorkshopAndGetContext("tenant-b-fin");

        AccountDtos.Create accReq = AccountDtos.Create.builder()
                .unitId(ctxA.unitId())
                .name("Caixa Oficina A")
                .type(AccountType.CASH_REGISTER)
                .initialBalance(BigDecimal.valueOf(1000.00))
                .build();

        String accJson = mockMvc.perform(post("/api/v1/finance/accounts")
                        .header("Authorization", "Bearer " + ctxA.token())
                        .header("X-Tenant-ID", ctxA.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AccountDtos.Response accountA = objectMapper.readValue(accJson, AccountDtos.Response.class);

        // Workshop B attempts to access Workshop A's account
        mockMvc.perform(get("/api/v1/finance/accounts/" + accountA.id())
                        .header("Authorization", "Bearer " + ctxB.token())
                        .header("X-Tenant-ID", ctxB.tenantId().toString()))
                .andExpect(status().isNotFound());
    }
}
