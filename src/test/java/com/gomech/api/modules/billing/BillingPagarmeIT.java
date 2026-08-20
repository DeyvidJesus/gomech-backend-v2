package com.gomech.api.modules.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.billing.api.dto.PaymentDtos;
import com.gomech.api.modules.billing.api.dto.SubscriptionResponse;
import com.gomech.api.modules.billing.domain.PaymentMethod;
import com.gomech.api.modules.billing.domain.PaymentStatus;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class BillingPagarmeIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record WorkshopContext(String token, UUID unitId, UUID tenantId, UUID userId) {}

    private WorkshopContext registerWorkshop(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@oficina.com.br";
        RegisterWorkshopRequest reg = new RegisterWorkshopRequest(
                "Oficina " + prefix,
                "Av. Paulista, 1000",
                3,
                List.of("Mecânica Geral"),
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
    @DisplayName("Complete Billing Flow: Subscription -> PIX Checkout -> Webhook order.paid -> Idempotent Replay -> Past Due Webhook -> Recovery")
    void completeBillingAndPagarmeSubscriptionFlow() throws Exception {
        WorkshopContext ctx = registerWorkshop("pagarme-flow");

        // 1. Check initial subscription (TRIALING)
        mockMvc.perform(get("/api/v1/billing/subscription")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("TRIALING")));

        // 2. Initiate PIX Checkout for PRO plan
        PaymentDtos.InitiatePaymentRequest checkoutReq = PaymentDtos.InitiatePaymentRequest.builder()
                .planCode("PRO")
                .method(PaymentMethod.PIX)
                .customerDocument("12345678909")
                .customerPhone("11988887777")
                .build();

        String paymentJson = mockMvc.perform(post("/api/v1/billing/payments/checkout")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.paymentMethod", is("PIX")))
                .andExpect(jsonPath("$.pixCopyPaste", notNullValue()))
                .andExpect(jsonPath("$.gatewayOrderId", notNullValue()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        PaymentDtos.PaymentResponse payment = objectMapper.readValue(paymentJson, PaymentDtos.PaymentResponse.class);

        // 3. Simulate Pagar.me Webhook: order.paid
        String webhookPayload = String.format("""
                {
                    "id": "evt_test_%s",
                    "type": "order.paid",
                    "data": {
                        "id": "%s",
                        "charges": [{"id": "%s"}]
                    }
                }
                """, UUID.randomUUID(), payment.gatewayOrderId(), payment.gatewayChargeId());

        mockMvc.perform(post("/api/v1/billing/webhooks/pagarme")
                        .header("X-Pagarme-Signature", "test-valid-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed", is(true)));

        // 4. Verify Subscription is now ACTIVE
        mockMvc.perform(get("/api/v1/billing/subscription")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        // 5. Test Webhook Idempotency (replay same payload)
        mockMvc.perform(post("/api/v1/billing/webhooks/pagarme")
                        .header("X-Pagarme-Signature", "test-valid-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed", is(true)));

        // 6. Simulate Webhook: invoice.payment_failed -> Subscription becomes PAST_DUE
        String failedWebhookPayload = String.format("""
                {
                    "id": "evt_test_failed_%s",
                    "type": "invoice.payment_failed",
                    "data": {
                        "id": "%s",
                        "charges": [{"id": "%s"}]
                    }
                }
                """, UUID.randomUUID(), payment.gatewayOrderId(), payment.gatewayChargeId());

        mockMvc.perform(post("/api/v1/billing/webhooks/pagarme")
                        .header("X-Pagarme-Signature", "test-valid-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failedWebhookPayload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/billing/subscription")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAST_DUE")));

        // 7. Recover Subscription with instant Card Checkout
        PaymentDtos.InitiatePaymentRequest cardReq = PaymentDtos.InitiatePaymentRequest.builder()
                .planCode("PRO")
                .method(PaymentMethod.CREDIT_CARD)
                .cardNumber("4242424242424242")
                .cardHolderName("OFICINA RECUPERADA")
                .cardExpMonth(12)
                .cardExpYear(2030)
                .cardCvv("999")
                .build();

        mockMvc.perform(post("/api/v1/billing/payments/checkout")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cardReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PAID")));

        // Verify Subscription recovered to ACTIVE
        mockMvc.perform(get("/api/v1/billing/subscription")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }
}
