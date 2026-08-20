package com.gomech.api.modules.billing.application;

import com.gomech.api.modules.billing.api.dto.PaymentDtos;
import com.gomech.api.modules.billing.domain.PaymentMethod;
import com.gomech.api.modules.billing.domain.PaymentStatus;
import com.gomech.api.modules.billing.domain.SubscriptionStatus;
import com.gomech.api.modules.billing.application.gateway.PagarmeGatewayClient;
import com.gomech.api.modules.billing.infrastructure.gateway.PagarmeDto;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlan;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Payment;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.BillingPlanRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.PaymentRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentInitiationServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private BillingPlanRepository planRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PagarmeGatewayClient pagarmeClient;

    @Mock
    private DelinquencyService delinquencyService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentInitiationService paymentService;

    private UUID tenantId;
    private Subscription subscription;
    private BillingPlan proPlan;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        proPlan = new BillingPlan();
        proPlan.setCode("PRO");
        proPlan.setName("Profissional");
        proPlan.setPrice(BigDecimal.valueOf(199.90));

        subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setPlan(proPlan);
        subscription.setPlanCode("PRO");
        subscription.setStatus(SubscriptionStatus.TRIALING.name());
    }

    @Test
    @DisplayName("Should initiate PIX payment and return QR Code and Copy/Paste code")
    void shouldInitiatePixPaymentSuccessfully() {
        PaymentDtos.InitiatePaymentRequest request = PaymentDtos.InitiatePaymentRequest.builder()
                .planCode("PRO")
                .method(PaymentMethod.PIX)
                .customerDocument("12345678900")
                .build();

        PagarmeDto.GatewayPaymentResult gwResult = PagarmeDto.GatewayPaymentResult.builder()
                .gatewayOrderId("or_12345")
                .gatewayChargeId("ch_12345")
                .status("pending")
                .paymentMethod("PIX")
                .amount(BigDecimal.valueOf(199.90))
                .pixQrCode("base64_qr")
                .pixCopyPaste("0002012658...")
                .pixExpiresAt(OffsetDateTime.now().plusDays(1))
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(proPlan));
        when(pagarmeClient.initiatePayment(any())).thenReturn(gwResult);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentDtos.PaymentResponse response = paymentService.initiatePayment(tenantId, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.PIX);
        assertThat(response.pixCopyPaste()).isEqualTo("0002012658...");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should initiate Credit Card payment, approve instantly, activate subscription and recover delinquency")
    void shouldInitiateCreditCardPaymentAndActivateSubscription() {
        PaymentDtos.InitiatePaymentRequest request = PaymentDtos.InitiatePaymentRequest.builder()
                .planCode("PRO")
                .method(PaymentMethod.CREDIT_CARD)
                .cardNumber("4242424242424242")
                .cardHolderName("OFICINA TESTE")
                .cardExpMonth(12)
                .cardExpYear(2028)
                .cardCvv("123")
                .build();

        PagarmeDto.GatewayPaymentResult gwResult = PagarmeDto.GatewayPaymentResult.builder()
                .gatewayOrderId("or_card_999")
                .gatewayChargeId("ch_card_999")
                .status("paid")
                .paymentMethod("CREDIT_CARD")
                .amount(BigDecimal.valueOf(199.90))
                .cardLastFour("4242")
                .cardBrand("VISA")
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(proPlan));
        when(pagarmeClient.initiatePayment(any())).thenReturn(gwResult);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentDtos.PaymentResponse response = paymentService.initiatePayment(tenantId, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE.name());
        verify(delinquencyService).recoverDelinquency(tenantId);
    }
}
