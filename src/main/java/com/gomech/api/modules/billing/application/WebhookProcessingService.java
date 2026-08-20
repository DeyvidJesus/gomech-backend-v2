package com.gomech.api.modules.billing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.billing.domain.PaymentStatus;
import com.gomech.api.modules.billing.domain.SubscriptionStatus;
import com.gomech.api.modules.billing.infrastructure.events.PaymentConfirmedEvent;
import com.gomech.api.modules.billing.infrastructure.events.PaymentFailedEvent;
import com.gomech.api.modules.billing.application.gateway.PagarmeGatewayClient;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Payment;
import com.gomech.api.modules.billing.infrastructure.persistence.model.ProcessedWebhook;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
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

        log.info("Processando evento '{}' (ID: {}) para Order: {}, Charge: {}", eventType, eventId, orderId, chargeId);

        switch (eventType.toLowerCase()) {
            case "order.paid":
            case "invoice.paid":
            case "charge.paid":
                handlePaymentPaid(orderId, chargeId);
                break;

            case "invoice.payment_failed":
            case "charge.payment_failed":
            case "order.payment_failed":
                handlePaymentFailed(orderId, chargeId, "Falha de cobrança notificada pelo Pagar.me");
                break;

            case "subscription.canceled":
                handleSubscriptionCanceled(data.path("id").asText());
                break;

            case "charge.refunded":
            case "order.refunded":
                handleChargeRefunded(orderId, chargeId, data.path("amount").asDouble(0.0));
                break;

            default:
                log.info("Evento Pagar.me '{}' não requer ação de estado", eventType);
        }

        return true;
    }

    private void handlePaymentPaid(String orderId, String chargeId) {
        Optional<Payment> optPayment = findPayment(orderId, chargeId);
        if (optPayment.isEmpty()) {
            log.warn("Nenhum pagamento local encontrado para Order: {}, Charge: {}", orderId, chargeId);
            return;
        }

        Payment payment = optPayment.get();
        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Pagamento {} já está como PAID", payment.getId());
            return;
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(OffsetDateTime.now());
        paymentRepository.save(payment);

        Subscription subscription = payment.getSubscription();
        if (subscription != null) {
            subscription.setStatus(SubscriptionStatus.ACTIVE.name());
            subscription.setNextBillingDate(LocalDate.now().plusMonths(1));
            subscription.setCurrentPeriodStart(OffsetDateTime.now());
            subscription.setCurrentPeriodEnd(OffsetDateTime.now().plusMonths(1));
            subscription.setDelinquentSince(null);
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
        }
    }

    private void handlePaymentFailed(String orderId, String chargeId, String reason) {
        Optional<Payment> optPayment = findPayment(orderId, chargeId);
        if (optPayment.isEmpty()) {
            log.warn("Nenhum pagamento local encontrado para marcar como FAILED");
            return;
        }

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
    }

    private void handleSubscriptionCanceled(String gatewaySubId) {
        if (gatewaySubId == null || gatewaySubId.isBlank()) return;
        subscriptionRepository.findByGatewaySubscriptionId(gatewaySubId).ifPresent(sub -> {
            delinquencyService.cancelSubscription(sub.getTenantId(), "Cancelamento recebido via webhook Pagar.me");
        });
    }

    private void handleChargeRefunded(String orderId, String chargeId, double amount) {
        findPayment(orderId, chargeId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundedAmount(BigDecimal.valueOf(amount));
            paymentRepository.save(payment);
            log.info("Pagamento {} marcado como estornado (R$ {})", payment.getId(), amount);
        });
    }

    private Optional<Payment> findPayment(String orderId, String chargeId) {
        if (orderId != null && !orderId.isBlank()) {
            Optional<Payment> byOrder = paymentRepository.findByGatewayOrderId(orderId);
            if (byOrder.isPresent()) return byOrder;
        }
        if (chargeId != null && !chargeId.isBlank()) {
            return paymentRepository.findByGatewayChargeId(chargeId);
        }
        return Optional.empty();
    }
}
