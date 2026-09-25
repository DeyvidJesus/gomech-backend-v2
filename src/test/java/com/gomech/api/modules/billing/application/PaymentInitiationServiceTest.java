package com.gomech.api.modules.billing.application;

import com.gomech.api.modules.billing.api.dto.PaymentDtos;
import com.gomech.api.modules.billing.application.gateway.PagarmeGatewayClient;
import com.gomech.api.modules.billing.infrastructure.gateway.PagarmeDto;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlan;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Payment;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.BillingPlanRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.PaymentRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.SubscriptionRepository;
import com.gomech.api.modules.iam.api.IamContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
    private IamContract iamContract;

    @Mock
    private PagarmeGatewayClient pagarmeClient;

    @InjectMocks
    private PaymentInitiationService paymentService;

    private UUID tenantId;
    private Subscription subscription;
    private BillingPlan proPlan;
    private IamContract.TenantContractDto tenantDto;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        tenantDto = new IamContract.TenantContractDto(
                tenantId,
                "Oficina Turbo Power",
                "Turbo Power Auto Center",
                "12.345.678/0001-90",
                "contato@turbopower.com.br",
                "11999999999",
                null,
                "ACTIVE"
        );

        proPlan = new BillingPlan();
        proPlan.setCode("PRO");
        proPlan.setName("GoMech Pro");
        proPlan.setPrice(BigDecimal.valueOf(199.00));
        proPlan.setPagarmePlanId("plan_veoYEdYhdxU9qJ9X");

        subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setPlan(proPlan);
        subscription.setPlanCode("PRO");
        subscription.setStatus("TRIALING");
    }

    @Test
    @DisplayName("Should generate Pagar.me Hosted Checkout session successfully")
    void shouldCreateHostedCheckoutSessionSuccessfully() {
        PaymentDtos.CreateCheckoutRequest request = PaymentDtos.CreateCheckoutRequest.builder()
                .planCode("PRO")
                .successUrl("https://gomech.app/billing?status=success")
                .cancelUrl("https://gomech.app/billing?status=canceled")
                .build();

        PagarmeDto.PaymentLinkResponse linkResponse = PagarmeDto.PaymentLinkResponse.builder()
                .id("pl_test12345678")
                .url("https://checkout.pagar.me/pl_test12345678")
                .status("active")
                .type("subscription")
                .planId("plan_veoYEdYhdxU9qJ9X")
                .createdAt(OffsetDateTime.now())
                .build();

        PagarmeDto.CustomerResponse custResponse = PagarmeDto.CustomerResponse.builder()
                .id("cus_test999")
                .name("Oficina Turbo Power")
                .email("contato@turbopower.com.br")
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(proPlan));
        when(iamContract.findTenantById(tenantId)).thenReturn(Optional.of(tenantDto));
        when(pagarmeClient.createOrGetCustomer(any())).thenReturn(custResponse);
        when(pagarmeClient.createHostedCheckout(any())).thenReturn(linkResponse);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentDtos.CheckoutSessionResponse response = paymentService.createHostedCheckoutSession(tenantId, request);

        assertThat(response).isNotNull();
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.pagar.me/pl_test12345678");
        assertThat(response.paymentLinkId()).isEqualTo("pl_test12345678");
        assertThat(response.planCode()).isEqualTo("PRO");
        assertThat(response.price()).isEqualByComparingTo(BigDecimal.valueOf(199.00));

        verify(paymentRepository).save(any(Payment.class));
        verify(subscriptionRepository).save(subscription);
    }
}
