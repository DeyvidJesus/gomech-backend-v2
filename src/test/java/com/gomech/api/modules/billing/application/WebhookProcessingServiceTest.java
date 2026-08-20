package com.gomech.api.modules.billing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.billing.domain.PaymentStatus;
import com.gomech.api.modules.billing.domain.SubscriptionStatus;
import com.gomech.api.modules.billing.application.gateway.PagarmeGatewayClient;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Payment;
import com.gomech.api.modules.billing.infrastructure.persistence.model.ProcessedWebhook;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.PaymentRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.ProcessedWebhookRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookProcessingServiceTest {

    @Mock
    private PagarmeGatewayClient pagarmeClient;

    @Mock
    private ProcessedWebhookRepository processedWebhookRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private DelinquencyService delinquencyService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WebhookProcessingService webhookService;

    private UUID tenantId;
    private Subscription subscription;
    private Payment payment;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setStatus(SubscriptionStatus.PAST_DUE.name());

        payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setTenantId(tenantId);
        payment.setSubscription(subscription);
        payment.setAmount(BigDecimal.valueOf(199.90));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setGatewayOrderId("or_webhook_123");
        payment.setGatewayChargeId("ch_webhook_123");
    }

    @Test
    @DisplayName("Should reject webhook with invalid signature")
    void shouldRejectInvalidSignature() {
        when(pagarmeClient.verifyWebhookSignature(any(), eq("invalid-sig"))).thenReturn(false);

        assertThatThrownBy(() -> webhookService.processWebhook("{}", "invalid-sig"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Should ignore already processed webhook event (Idempotency)")
    void shouldIgnoreReplayedWebhook() {
        String payload = "{\"id\":\"evt_123\",\"type\":\"order.paid\",\"data\":{}}";

        when(pagarmeClient.verifyWebhookSignature(any(), any())).thenReturn(true);
        when(processedWebhookRepository.existsByEventId("evt_123")).thenReturn(true);

        boolean result = webhookService.processWebhook(payload, "valid-sig");

        assertThat(result).isTrue();
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should process order.paid event, mark payment PAID, activate subscription and recover delinquency")
    void shouldProcessOrderPaidSuccessfully() {
        String payload = """
                {
                    "id": "evt_order_paid_001",
                    "type": "order.paid",
                    "data": {
                        "id": "or_webhook_123",
                        "charges": [{"id": "ch_webhook_123"}]
                    }
                }
                """;

        when(pagarmeClient.verifyWebhookSignature(any(), any())).thenReturn(true);
        when(processedWebhookRepository.existsByEventId("evt_order_paid_001")).thenReturn(false);
        when(paymentRepository.findByGatewayOrderId("or_webhook_123")).thenReturn(Optional.of(payment));

        boolean result = webhookService.processWebhook(payload, "valid-sig");

        assertThat(result).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE.name());
        verify(processedWebhookRepository).save(any(ProcessedWebhook.class));
        verify(delinquencyService).recoverDelinquency(tenantId);
    }

    @Test
    @DisplayName("Should process invoice.payment_failed event, mark payment FAILED and mark tenant as delinquent")
    void shouldProcessPaymentFailedAndMarkDelinquent() {
        String payload = """
                {
                    "id": "evt_failed_002",
                    "type": "invoice.payment_failed",
                    "data": {
                        "id": "or_webhook_123",
                        "charges": [{"id": "ch_webhook_123"}]
                    }
                }
                """;

        when(pagarmeClient.verifyWebhookSignature(any(), any())).thenReturn(true);
        when(processedWebhookRepository.existsByEventId("evt_failed_002")).thenReturn(false);
        when(paymentRepository.findByGatewayOrderId("or_webhook_123")).thenReturn(Optional.of(payment));

        boolean result = webhookService.processWebhook(payload, "valid-sig");

        assertThat(result).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(delinquencyService).markDelinquent(eq(tenantId), any());
    }
}
