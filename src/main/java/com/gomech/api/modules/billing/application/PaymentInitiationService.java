package com.gomech.api.modules.billing.application;

import com.gomech.api.modules.billing.api.dto.PaymentDtos;
import com.gomech.api.modules.billing.domain.PaymentMethod;
import com.gomech.api.modules.billing.domain.PaymentStatus;
import com.gomech.api.modules.billing.domain.SubscriptionStatus;
import com.gomech.api.modules.billing.infrastructure.events.PaymentConfirmedEvent;
import com.gomech.api.modules.billing.infrastructure.events.SubscriptionStatusChangedEvent;
import com.gomech.api.modules.billing.infrastructure.gateway.PagarmeDto;
import com.gomech.api.modules.billing.application.gateway.PagarmeGatewayClient;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlan;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Payment;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.BillingPlanRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.PaymentRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentInitiationService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final BillingPlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final PagarmeGatewayClient pagarmeClient;
    private final DelinquencyService delinquencyService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PaymentDtos.PaymentResponse initiatePayment(UUID tenantId, PaymentDtos.InitiatePaymentRequest request) {
        log.info("Iniciando fluxo de checkout/pagamento para tenant {} via {}", tenantId, request.method());

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> subscriptionService.createDefaultTrialSubscription(tenantId));

        BillingPlan targetPlan;
        if (request.planCode() != null && !request.planCode().isBlank()) {
            targetPlan = planRepository.findByCode(request.planCode().trim().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Plano não encontrado: " + request.planCode()));
        } else if (subscription.getPlan() != null) {
            targetPlan = subscription.getPlan();
        } else {
            targetPlan = planRepository.findByCode("PRO")
                    .orElseThrow(() -> new IllegalStateException("Plano padrão PRO não encontrado"));
        }

        BigDecimal amount = targetPlan.getPrice();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = BigDecimal.valueOf(149.90); // Fallback standard price if trial or unset
        }

        PagarmeDto.CardDetails cardDetails = null;
        if (request.method() == PaymentMethod.CREDIT_CARD && request.cardNumber() != null) {
            cardDetails = PagarmeDto.CardDetails.builder()
                    .number(request.cardNumber().replaceAll("\\D", ""))
                    .holderName(request.cardHolderName() != null ? request.cardHolderName().toUpperCase() : "CLIENTE")
                    .expMonth(request.cardExpMonth() != null ? request.cardExpMonth() : 12)
                    .expYear(request.cardExpYear() != null ? request.cardExpYear() : 2028)
                    .cvv(request.cardCvv() != null ? request.cardCvv() : "123")
                    .build();
        }

        PagarmeDto.CreatePaymentRequest gwRequest = PagarmeDto.CreatePaymentRequest.builder()
                .tenantId(tenantId)
                .subscriptionId(subscription.getId())
                .amount(amount)
                .method(request.method())
                .customerName(request.cardHolderName() != null ? request.cardHolderName() : "Oficina GoMech")
                .customerEmail("financeiro@oficina.com.br")
                .customerDocument(request.customerDocument() != null ? request.customerDocument() : "12345678901")
                .customerPhone(request.customerPhone() != null ? request.customerPhone() : "11999998888")
                .cardDetails(cardDetails)
                .cardToken(request.cardToken())
                .installments(request.installments() != null ? request.installments() : 1)
                .boletoDueDate(LocalDate.now().plusDays(3))
                .build();

        PagarmeDto.GatewayPaymentResult gwResult = pagarmeClient.initiatePayment(gwRequest);

        boolean isPaid = "paid".equalsIgnoreCase(gwResult.status());
        PaymentStatus initialStatus = isPaid ? PaymentStatus.PAID : PaymentStatus.PENDING;

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setTenantId(tenantId);
        payment.setSubscription(subscription);
        payment.setAmount(amount);
        payment.setStatus(initialStatus);
        payment.setPaymentMethod(request.method());
        payment.setDueDate(request.method() == PaymentMethod.BOLETO ? gwResult.boletoDueDate() : LocalDate.now());
        payment.setGatewayOrderId(gwResult.gatewayOrderId());
        payment.setGatewayChargeId(gwResult.gatewayChargeId());
        payment.setGatewayPaymentId(gwResult.gatewayPaymentId());
        payment.setPixQrCode(gwResult.pixQrCode());
        payment.setPixQrCodeUrl(gwResult.pixQrCodeUrl());
        payment.setPixCopyPaste(gwResult.pixCopyPaste());
        payment.setPixExpiresAt(gwResult.pixExpiresAt());
        payment.setBoletoBarcode(gwResult.boletoBarcode());
        payment.setBoletoUrl(gwResult.boletoUrl());
        payment.setBoletoDueDate(gwResult.boletoDueDate());
        payment.setInstallments(request.installments() != null ? request.installments() : 1);
        payment.setGatewayResponseRaw(gwResult.rawResponse());

        if (isPaid) {
            payment.setPaidAt(OffsetDateTime.now());
        }

        payment = paymentRepository.save(payment);

        // Se o pagamento já foi aprovado instantaneamente (Cartão de Crédito)
        if (isPaid) {
            SubscriptionStatus prevStatus = SubscriptionStatus.valueOf(subscription.getStatus());
            subscription.setPlan(targetPlan);
            subscription.setPlanCode(targetPlan.getCode());
            subscription.setPlanName(targetPlan.getName());
            subscription.setStatus(SubscriptionStatus.ACTIVE.name());
            subscription.setPaymentMethod(request.method().name());
            subscription.setCardLastFour(gwResult.cardLastFour());
            subscription.setCardBrand(gwResult.cardBrand());
            subscription.setCurrentPeriodStart(OffsetDateTime.now());
            subscription.setCurrentPeriodEnd(OffsetDateTime.now().plusMonths(1));
            subscription.setNextBillingDate(LocalDate.now().plusMonths(1));
            subscription.setDelinquentSince(null);
            subscriptionRepository.save(subscription);

            delinquencyService.recoverDelinquency(tenantId);

            eventPublisher.publishEvent(new SubscriptionStatusChangedEvent(
                    tenantId, subscription.getId(), targetPlan.getCode(), prevStatus, SubscriptionStatus.ACTIVE, OffsetDateTime.now()));

            eventPublisher.publishEvent(new PaymentConfirmedEvent(
                    tenantId, subscription.getId(), payment.getId(), amount, request.method(), gwResult.gatewayOrderId(), OffsetDateTime.now()));
        }

        log.info("Pagamento registrado: ID {}, Status {}", payment.getId(), payment.getStatus());
        return mapToResponse(payment, gwResult.cardLastFour(), gwResult.cardBrand());
    }

    @Transactional(readOnly = true)
    public Page<PaymentDtos.PaymentResponse> listPayments(UUID tenantId, Pageable pageable) {
        Page<Payment> page = paymentRepository.findAllByTenantId(tenantId, pageable);
        return page.map(p -> mapToResponse(p, null, null));
    }

    @Transactional(readOnly = true)
    public PaymentDtos.PaymentResponse getPayment(UUID id, UUID tenantId) {
        Payment payment = paymentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado: " + id));
        return mapToResponse(payment, null, null);
    }

    private PaymentDtos.PaymentResponse mapToResponse(Payment p, String cardLastFour, String cardBrand) {
        return PaymentDtos.PaymentResponse.builder()
                .id(p.getId())
                .tenantId(p.getTenantId())
                .subscriptionId(p.getSubscription() != null ? p.getSubscription().getId() : null)
                .amount(p.getAmount())
                .status(p.getStatus())
                .paymentMethod(p.getPaymentMethod())
                .dueDate(p.getDueDate())
                .paidAt(p.getPaidAt())
                .gatewayOrderId(p.getGatewayOrderId())
                .gatewayChargeId(p.getGatewayChargeId())
                .gatewayPaymentId(p.getGatewayPaymentId())
                .pixQrCode(p.getPixQrCode())
                .pixQrCodeUrl(p.getPixQrCodeUrl())
                .pixCopyPaste(p.getPixCopyPaste())
                .pixExpiresAt(p.getPixExpiresAt())
                .boletoBarcode(p.getBoletoBarcode())
                .boletoUrl(p.getBoletoUrl())
                .boletoDueDate(p.getBoletoDueDate())
                .installments(p.getInstallments())
                .cardLastFour(cardLastFour)
                .cardBrand(cardBrand)
                .createdAt(p.getCreatedAt())
                .build();
    }
}
