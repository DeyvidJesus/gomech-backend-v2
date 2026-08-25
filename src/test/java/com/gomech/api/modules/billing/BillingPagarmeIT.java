package com.gomech.api.modules.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.billing.api.dto.PaymentDtos;
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
    @DisplayName("Complete Billing Flow: Trial Signup -> Hosted Checkout Generation -> Webhook subscription.created & charge.paid -> Active -> Past Due -> Recovery")
    void completeHostedCheckoutAndWebhookFlow() throws Exception {
        WorkshopContext ctx = registerWorkshop("hosted-flow");

        // 1. Check initial subscription (TRIALING, zero card entered)
        mockMvc.perform(get("/api/v1/billing/subscription")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("TRIALING")));

        // 2. Generate Hosted Checkout Session for PRO plan
        PaymentDtos.CreateCheckoutRequest checkoutReq = PaymentDtos.CreateCheckoutRequest.builder()
                .planCode("PRO")
                .successUrl("https://gomech.app/billing?status=success")
                .cancelUrl("https://gomech.app/billing?status=canceled")
                .build();

        String checkoutJson = mockMvc.perform(post("/api/v1/billing/payments/checkout")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkoutUrl", notNullValue()))
                .andExpect(jsonPath("$.paymentLinkId", notNullValue()))
                .andExpect(jsonPath("$.planCode", is("PRO")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        PaymentDtos.CheckoutSessionResponse session = objectMapper.readValue(checkoutJson, PaymentDtos.CheckoutSessionResponse.class);

        // 3. Simulate Pagar.me Webhook: subscription.created
        String subCreatedPayload = String.format("""
                {
                    "id": "evt_sub_created_%s",
                    "type": "subscription.created",
                    "data": {
                        "id": "sub_gw_12345",
                        "status": "active",
                        "payment_link_id": "%s",
                        "metadata": {
                            "tenant_id": "%s",
                            "plan_code": "PRO"
                        },
                        "customer": {
                            "id": "cus_12345"
                        },
                        "card": {
                            "brand": "Mastercard",
                            "last_four_digits": "5678"
                        }
                    }
                }
                """, UUID.randomUUID(), session.paymentLinkId(), ctx.tenantId());

        mockMvc.perform(post("/api/v1/billing/webhooks/pagarme")
                        .header("X-Pagarme-Signature", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subCreatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed", is(true)));

        // 4. Verify Subscription is now ACTIVE
        mockMvc.perform(get("/api/v1/billing/subscription")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.cardBrand", is("Mastercard")))
                .andExpect(jsonPath("$.cardLastFour", is("5678")));

        // 5. Test Webhook Idempotency (replay same payload)
        mockMvc.perform(post("/api/v1/billing/webhooks/pagarme")
                        .header("X-Pagarme-Signature", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subCreatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed", is(true)));

        // 6. Simulate Webhook: charge.payment_failed -> Subscription becomes PAST_DUE
        String failedWebhookPayload = String.format("""
                {
                    "id": "evt_test_failed_%s",
                    "type": "charge.payment_failed",
                    "data": {
                        "id": "ch_failed_123",
                        "payment_link_id": "%s"
                    }
                }
                """, UUID.randomUUID(), session.paymentLinkId());

        mockMvc.perform(post("/api/v1/billing/webhooks/pagarme")
                        .header("X-Pagarme-Signature", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failedWebhookPayload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/billing/subscription")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAST_DUE")));

        // 7. Simulate Webhook: charge.paid -> Subscription recovered to ACTIVE
        String paidWebhookPayload = String.format("""
                {
                    "id": "evt_test_paid_%s",
                    "type": "charge.paid",
                    "data": {
                        "id": "ch_recovered_123",
                        "order_id": "or_recovered_123",
                        "payment_link_id": "%s",
                        "amount": 19900,
                        "last_transaction": {
                            "card": {
                                "brand": "Visa",
                                "last_four_digits": "4242"
                            }
                        }
                    }
                }
                """, UUID.randomUUID(), session.paymentLinkId());

        mockMvc.perform(post("/api/v1/billing/webhooks/pagarme")
                        .header("X-Pagarme-Signature", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paidWebhookPayload))
                .andExpect(status().isOk());

        // Verify Subscription recovered to ACTIVE
        mockMvc.perform(get("/api/v1/billing/subscription")
                        .header("Authorization", "Bearer " + ctx.token())
                        .header("X-Tenant-ID", ctx.tenantId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }
}
