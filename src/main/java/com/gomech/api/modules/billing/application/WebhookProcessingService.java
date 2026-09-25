package com.gomech.api.modules.billing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.billing.domain.PaymentMethod;
import com.gomech.api.modules.billing.domain.PaymentStatus;
import com.gomech.api.modules.billing.domain.SubscriptionStatus;
import com.gomech.api.modules.billing.events.PaymentConfirmedEvent;
import com.gomech.api.modules.billing.events.PaymentFailedEvent;
import com.gomech.api.modules.billing.application.gateway.PagarmeGatewayClient;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlan;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Payment;
import com.gomech.api.modules.billing.infrastructure.persistence.model.ProcessedWebhook;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.BillingPlanRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.PaymentRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.ProcessedWebhookRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookProcessingService {

    private final PagarmeGatewayClient pagarmeClient;
    private final ProcessedWebhookRepository processedWebhookRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final DelinquencyService delinquencyService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public boolean processWebhook(String rawPayload, String signature) {
        log.info("Recebendo webhook Pagar.me para processamento");

        if (!pagarmeClient.verifyWebhookSignature(rawPayload, signature)) {
            log.error("Assinatura inválida do webhook Pagar.me");
            throw new SecurityException("Assinatura do webhook Pagar.me é inválida.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            log.error("Erro ao ler JSON do webhook Pagar.me: {}", e.getMessage());
            throw new IllegalArgumentException("Payload inválido");
        }

        String eventId = root.path("id").asText();
        String eventType = root.path("type").asText();

        if (eventId == null || eventId.isBlank()) {
            eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        }

        // Idempotency Check
        if (processedWebhookRepository.existsByEventId(eventId)) {
            log.warn("Webhook idempotência: evento {} já foi processado anteriormente. Ignorando replay.", eventId);
            return true;
        }

        // Persist idempotency record before applying mutations
        ProcessedWebhook record = new ProcessedWebhook();
        record.setId(UUID.randomUUID());
        record.setEventId(eventId);
        record.setEventType(eventType);
        record.setSource("PAGARME");
        record.setStatus("PROCESSED");
        processedWebhookRepository.save(record);

        JsonNode data = root.path("data");
        String orderId = data.path("id").asText();
        if (orderId == null || orderId.isBlank()) {
            orderId = data.path("order").path("id").asText();
        }
        String chargeId = data.path("charges").isArray() && !data.path("charges").isEmpty()
                ? data.path("charges").get(0).path("id").asText()
                : data.path("charge").path("id").asText();

        String paymentLinkId = data.path("payment_link_id").asText();
        if (paymentLinkId == null || paymentLinkId.isBlank()) {
            paymentLinkId = data.path("payment_link").path("id").asText();
        }

        String subscriptionId = data.path("subscription_id").asText();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            subscriptionId = data.path("subscription").path("id").asText();
        }

        log.info("Processando evento '{}' (ID: {}) | Order: {}, Charge: {}, Sub: {}, Link: {}",
                eventType, eventId, orderId, chargeId, subscriptionId, paymentLinkId);

        switch (eventType.toLowerCase()) {
            case "subscription.created":
            case "subscription.updated":
                handleSubscriptionCreatedOrUpdated(data);
                break;

            case "order.paid":
            case "invoice.paid":
            case "charge.paid":
                handlePaymentPaid(data, orderId, chargeId, paymentLinkId, subscriptionId);
                break;

            case "invoice.payment_failed":
            case "charge.payment_failed":
            case "order.payment_failed":
                handlePaymentFailed(data, orderId, chargeId, paymentLinkId, subscriptionId, "Falha de cobrança notificada pelo Pagar.me");
                break;

            case "subscription.canceled":
            case "subscription.expired":
                handleSubscriptionCanceled(data.path("id").asText());
                break;

            case "charge.refunded":
            case "order.refunded":
                handleChargeRefunded(orderId, chargeId, data.path("amount").asDouble(0.0));
                break;

            default:
                log.info("Evento Pagar.me '{}' recebido com sucesso (sem transição de estado necessária)", eventType);
        }

        return true;
    }

    private void handleSubscriptionCreatedOrUpdated(JsonNode data) {
        String gwSubId = data.path("id").asText();
        String customerId = data.path("customer").path("id").asText();
        String planId = data.path("plan").path("id").asText();
        String status = data.path("status").asText();

        String tenantIdStr = data.path("metadata").path("tenant_id").asText();
        UUID tenantId = null;
        if (tenantIdStr != null && !tenantIdStr.isBlank()) {
            try {
                tenantId = UUID.fromString(tenantIdStr);
            } catch (Exception ignored) {}
        }

        Subscription subscription = null;
        if (tenantId != null) {
            subscription = subscriptionRepository.findByTenantId(tenantId).orElse(null);
        }
        if (subscription == null && gwSubId != null && !gwSubId.isBlank()) {
            subscription = subscriptionRepository.findByGatewaySubscriptionId(gwSubId).orElse(null);
        }
        if (subscription == null && customerId != null && !customerId.isBlank()) {
            subscription = subscriptionRepository.findByGatewayCustomerId(customerId).orElse(null);
        }

        if (subscription != null) {
            subscription.setGatewaySubscriptionId(gwSubId);
            if (customerId != null && !customerId.isBlank()) {
                subscription.setGatewayCustomerId(customerId);
            }

            if ("active".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status)) {
                subscription.setStatus(SubscriptionStatus.ACTIVE.name());
                subscription.setNextBillingDate(LocalDate.now().plusMonths(1));
                subscription.setCurrentPeriodStart(OffsetDateTime.now());
                subscription.setCurrentPeriodEnd(OffsetDateTime.now().plusMonths(1));
                subscription.setDelinquentSince(null);
                delinquencyService.recoverDelinquency(subscription.getTenantId());
            }

            if (planId != null && !planId.isBlank()) {
                Optional<BillingPlan> optPlan = billingPlanRepository.findByPagarmePlanId(planId);
                if (optPlan.isPresent()) {
                    BillingPlan p = optPlan.get();
                    subscription.setPlan(p);
                    subscription.setPlanCode(p.getCode());
                    subscription.setPlanName(p.getName());
                }
            }

            // Extract card brand / last four if present in current cycle
            JsonNode card = data.path("card");
            if (card.isMissingNode() || card.isNull()) {
                card = data.path("current_cycle").path("charge").path("last_transaction").path("card");
            }
            if (!card.isMissingNode() && !card.isNull()) {
                subscription.setCardBrand(card.path("brand").asText(null));
                subscription.setCardLastFour(card.path("last_four_digits").asText(null));
                subscription.setPaymentMethod("CREDIT_CARD");
            }

            subscriptionRepository.save(subscription);
            log.info("Assinatura {} sincronizada com sucesso via webhook Pagar.me", subscription.getId());
        }
    }

    private void handlePaymentPaid(JsonNode data, String orderId, String chargeId, String paymentLinkId, String gwSubId) {
        Optional<Payment> optPayment = findPayment(orderId, chargeId, paymentLinkId);

        Payment payment;
        if (optPayment.isPresent()) {
            payment = optPayment.get();
        } else {
            // Busca a assinatura por metadados ou ID de link
            Subscription subscription = resolveSubscription(data, gwSubId, paymentLinkId);
            if (subscription == null) {
                log.warn("Não foi possível correlacionar pagamento do webhook: Order={}, Charge={}, Link={}", orderId, chargeId, paymentLinkId);
                return;
            }

            payment = new Payment();
            payment.setId(UUID.randomUUID());
            payment.setTenantId(subscription.getTenantId());
            payment.setSubscription(subscription);
            payment.setAmount(BigDecimal.valueOf(data.path("amount").asDouble(0.0)).divide(BigDecimal.valueOf(100)));
            payment.setPaymentMethod(PaymentMethod.CREDIT_CARD);
            payment.setDueDate(LocalDate.now());
            payment.setGatewayPaymentLinkId(paymentLinkId);
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Pagamento {} já está como PAID", payment.getId());
            return;
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(OffsetDateTime.now());
        payment.setGatewayOrderId(orderId);
        payment.setGatewayChargeId(chargeId);
        paymentRepository.save(payment);

        Subscription subscription = payment.getSubscription();
        if (subscription != null) {
            subscription.setStatus(SubscriptionStatus.ACTIVE.name());
            subscription.setNextBillingDate(LocalDate.now().plusMonths(1));
            subscription.setCurrentPeriodStart(OffsetDateTime.now());
            subscription.setCurrentPeriodEnd(OffsetDateTime.now().plusMonths(1));
            subscription.setDelinquentSince(null);

            if (gwSubId != null && !gwSubId.isBlank()) {
                subscription.setGatewaySubscriptionId(gwSubId);
            }

            // Extract card brand / last four if present
            JsonNode card = data.path("last_transaction").path("card");
            if (!card.isMissingNode() && !card.isNull()) {
                subscription.setCardBrand(card.path("brand").asText(subscription.getCardBrand()));
                subscription.setCardLastFour(card.path("last_four_digits").asText(subscription.getCardLastFour()));
            }

            subscriptionRepository.save(subscription);
            delinquencyService.recoverDelinquency(subscription.getTenantId());

            eventPublisher.publishEvent(new PaymentConfirmedEvent(
                    payment.getTenantId(),
                    subscription.getId(),
                    payment.getId(),
                    payment.getAmount(),
                    payment.getPaymentMethod(),
                    payment.getGatewayOrderId(),
                    OffsetDateTime.now()
            ));
            log.info("Pagamento {} e assinatura {} ativados com sucesso após webhook 'paid'", payment.getId(), subscription.getId());
        }
    }

    private void handlePaymentFailed(JsonNode data, String orderId, String chargeId, String paymentLinkId, String subscriptionId, String reason) {
        Optional<Payment> optPayment = findPayment(orderId, chargeId, paymentLinkId);
        if (optPayment.isPresent()) {
            Payment payment = optPayment.get();
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            if (payment.getSubscription() != null) {
                delinquencyService.markDelinquent(payment.getSubscription().getTenantId(), reason);

                eventPublisher.publishEvent(new PaymentFailedEvent(
                        payment.getTenantId(),
                        payment.getSubscription().getId(),
                        payment.getId(),
                        payment.getAmount(),
                        reason,
                        OffsetDateTime.now()
                ));
            }
        } else {
            Subscription subscription = resolveSubscription(data, subscriptionId, paymentLinkId);
            if (subscription != null) {
                delinquencyService.markDelinquent(subscription.getTenantId(), reason);
                eventPublisher.publishEvent(new PaymentFailedEvent(
                        subscription.getTenantId(),
                        subscription.getId(),
                        null,
                        subscription.getPlan() != null ? subscription.getPlan().getPrice() : BigDecimal.ZERO,
                        reason,
                        OffsetDateTime.now()
                ));
            } else {
                log.warn("Nenhum pagamento ou assinatura local encontrada para marcar como FAILED");
            }
        }
    }

    private void handleSubscriptionCanceled(String gatewaySubId) {
        if (gatewaySubId == null || gatewaySubId.isBlank()) return;
        subscriptionRepository.findByGatewaySubscriptionId(gatewaySubId).ifPresent(sub -> {
            delinquencyService.cancelSubscription(sub.getTenantId(), "Cancelamento recebido via webhook Pagar.me");
        });
    }

    private void handleChargeRefunded(String orderId, String chargeId, double amountInCents) {
        findPayment(orderId, chargeId, null).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundedAmount(BigDecimal.valueOf(amountInCents).divide(BigDecimal.valueOf(100)));
            paymentRepository.save(payment);
            log.info("Pagamento {} marcado como estornado (R$ {})", payment.getId(), payment.getRefundedAmount());
        });
    }

    private Optional<Payment> findPayment(String orderId, String chargeId, String paymentLinkId) {
        if (orderId != null && !orderId.isBlank()) {
            Optional<Payment> byOrder = paymentRepository.findByGatewayOrderId(orderId);
            if (byOrder.isPresent()) return byOrder;
        }
        if (chargeId != null && !chargeId.isBlank()) {
            Optional<Payment> byCharge = paymentRepository.findByGatewayChargeId(chargeId);
            if (byCharge.isPresent()) return byCharge;
        }
        if (paymentLinkId != null && !paymentLinkId.isBlank()) {
            return paymentRepository.findByGatewayPaymentLinkId(paymentLinkId);
        }
        return Optional.empty();
    }

    private Subscription resolveSubscription(JsonNode data, String gwSubId, String paymentLinkId) {
        if (gwSubId != null && !gwSubId.isBlank()) {
            Optional<Subscription> bySub = subscriptionRepository.findByGatewaySubscriptionId(gwSubId);
            if (bySub.isPresent()) return bySub.get();
        }
        if (paymentLinkId != null && !paymentLinkId.isBlank()) {
            Optional<Subscription> byLink = subscriptionRepository.findByGatewayPaymentLinkId(paymentLinkId);
            if (byLink.isPresent()) return byLink.get();
        }
        String tenantIdStr = data.path("metadata").path("tenant_id").asText();
        if (tenantIdStr != null && !tenantIdStr.isBlank()) {
            try {
                return subscriptionRepository.findByTenantId(UUID.fromString(tenantIdStr)).orElse(null);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
