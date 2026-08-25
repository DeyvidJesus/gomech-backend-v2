package com.gomech.api.modules.billing.application;

import com.gomech.api.modules.billing.api.dto.PaymentDtos;
import com.gomech.api.modules.billing.domain.PaymentMethod;
import com.gomech.api.modules.billing.domain.PaymentStatus;
import com.gomech.api.modules.billing.infrastructure.gateway.PagarmeDto;
import com.gomech.api.modules.billing.application.gateway.PagarmeGatewayClient;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlan;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Payment;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.BillingPlanRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.PaymentRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.SubscriptionRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Tenant;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentInitiationService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final BillingPlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final TenantRepository tenantRepository;
    private final PagarmeGatewayClient pagarmeClient;

    /**
     * Cria uma sessão oficial de Checkout Hospedado (Payment Link) na Pagar.me V5 para assinatura de plano.
     */
    @Transactional
    public PaymentDtos.CheckoutSessionResponse createHostedCheckoutSession(UUID tenantId, PaymentDtos.CreateCheckoutRequest request) {
        log.info("Iniciando geração de Pagar.me Hosted Checkout para tenant {} no plano {}", tenantId, request.planCode());

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> subscriptionService.createDefaultTrialSubscription(tenantId));

        String planCode = (request.planCode() != null && !request.planCode().isBlank())
                ? request.planCode().trim().toUpperCase()
                : "PRO";

        BillingPlan targetPlan = planRepository.findByCode(planCode)
                .orElseGet(() -> planRepository.findByCode("PRO")
                        .orElseThrow(() -> new IllegalStateException("Plano PRO não encontrado")));

        // Resolução do ID do plano no Pagar.me
        String pagarmePlanId = targetPlan.getPagarmePlanId();
        if (pagarmePlanId == null || pagarmePlanId.isBlank()) {
            pagarmePlanId = switch (targetPlan.getCode()) {
                case "STARTER" -> "plan_QjP9YkMU7KHxomNz";
                case "ENTERPRISE" -> "plan_2jVwBLXIVEiKR3xq";
                default -> "plan_veoYEdYhdxU9qJ9X"; // PRO
            };
            targetPlan.setPagarmePlanId(pagarmePlanId);
            planRepository.save(targetPlan);
        }

        // Verificação de idempotência: se já possui assinatura ATIVA no mesmo plano, rejeitar
        if ("ACTIVE".equalsIgnoreCase(subscription.getStatus()) && targetPlan.getCode().equalsIgnoreCase(subscription.getPlanCode())) {
            throw new IllegalArgumentException("Sua oficina já possui uma assinatura ativa para o plano " + targetPlan.getName() + ".");
        }

        // Cancelar intenções de pagamento pendentes anteriores para evitar pagamentos duplicados
        List<Payment> existingPending = paymentRepository.findAllByTenantIdAndStatus(tenantId, PaymentStatus.PENDING);
        for (Payment p : existingPending) {
            p.setStatus(PaymentStatus.CANCELED);
            paymentRepository.save(p);
        }

        // Obter ou criar Customer no Pagar.me
        String customerId = subscription.getGatewayCustomerId();
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);

        if (customerId == null || customerId.isBlank()) {
            if (tenant != null && tenant.getGatewayCustomerId() != null) {
                customerId = tenant.getGatewayCustomerId();
            } else if (tenant != null) {
                try {
                    PagarmeDto.CustomerResponse custRes = pagarmeClient.createOrGetCustomer(
                            PagarmeDto.CustomerRequest.builder()
                                    .name(tenant.getName() != null ? tenant.getName() : "Oficina GoMech")
                                    .email(tenant.getEmail() != null ? tenant.getEmail() : "financeiro@oficina.com.br")
                                    .document(tenant.getCnpj())
                                    .phone(tenant.getPhone())
                                    .type("company")
                                    .build()
                    );
                    if (custRes != null && custRes.id() != null) {
                        customerId = custRes.id();
                        tenant.setGatewayCustomerId(customerId);
                        tenantRepository.save(tenant);
                    }
                } catch (Exception ex) {
                    log.warn("Customer prévio não registrado no gateway; o checkout coletará os dados diretamente: {}", ex.getMessage());
                }
            }
            if (customerId != null) {
                subscription.setGatewayCustomerId(customerId);
            }
        }

        // Criar Payment Link de Assinatura no Pagar.me
        PagarmeDto.PaymentLinkResponse linkResponse = pagarmeClient.createHostedCheckout(
                PagarmeDto.CreateHostedCheckoutRequest.builder()
                        .tenantId(tenantId)
                        .subscriptionId(subscription.getId())
                        .pagarmePlanId(pagarmePlanId)
                        .customerId(customerId)
                        .planCode(targetPlan.getCode())
                        .price(targetPlan.getPrice())
                        .successUrl(request.successUrl())
                        .cancelUrl(request.cancelUrl())
                        .metadata(Map.of(
                                "tenant_id", tenantId.toString(),
                                "plan_code", targetPlan.getCode(),
                                "subscription_id", subscription.getId().toString()
                        ))
                        .build()
        );

        subscription.setGatewayPaymentLinkId(linkResponse.id());
        subscriptionRepository.save(subscription);

        // Registrar intenção de pagamento pendente
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setTenantId(tenantId);
        payment.setSubscription(subscription);
        payment.setAmount(targetPlan.getPrice());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod(PaymentMethod.CREDIT_CARD); // Padrão checkout
        payment.setDueDate(LocalDate.now().plusDays(1));
        payment.setGatewayPaymentLinkId(linkResponse.id());
        payment = paymentRepository.save(payment);

        log.info("Sessão de Hosted Checkout gerada com sucesso: ID={}, Link={}, URL={}",
                payment.getId(), linkResponse.id(), linkResponse.url());

        return PaymentDtos.CheckoutSessionResponse.builder()
                .checkoutUrl(linkResponse.url())
                .planCode(targetPlan.getCode())
                .price(targetPlan.getPrice())
                .paymentLinkId(linkResponse.id())
                .paymentId(payment.getId())
                .build();
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
                .gatewayPaymentLinkId(p.getGatewayPaymentLinkId())
                .pixQrCode(p.getPixQrCode())
                .pixQrCodeUrl(p.getPixQrCodeUrl())
                .pixCopyPaste(p.getPixCopyPaste())
                .pixExpiresAt(p.getPixExpiresAt())
                .boletoBarcode(p.getBoletoBarcode())
                .boletoUrl(p.getBoletoUrl())
                .boletoDueDate(p.getBoletoDueDate())
                .installments(p.getInstallments())
                .cardLastFour(cardLastFour != null ? cardLastFour : (p.getSubscription() != null ? p.getSubscription().getCardLastFour() : null))
                .cardBrand(cardBrand != null ? cardBrand : (p.getSubscription() != null ? p.getSubscription().getCardBrand() : null))
                .createdAt(p.getCreatedAt())
                .build();
    }
}
