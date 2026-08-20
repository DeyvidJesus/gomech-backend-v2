package com.gomech.api.modules.billing.application.gateway;

import com.gomech.api.modules.billing.infrastructure.gateway.PagarmeDto;

import java.math.BigDecimal;

public interface PagarmeGatewayClient {

    /**
     * Inicia uma transação de pagamento no Pagar.me (PIX, Cartão ou Boleto).
     */
    PagarmeDto.GatewayPaymentResult initiatePayment(PagarmeDto.CreatePaymentRequest request);

    /**
     * Cancela uma assinatura recorrente no gateway.
     */
    boolean cancelSubscription(String gatewaySubscriptionId);

    /**
     * Realiza estorno/reembolso de cobrança.
     */
    boolean refundCharge(String gatewayChargeId, BigDecimal amount);

    /**
     * Valida a assinatura criptográfica do Webhook do Pagar.me.
     */
    boolean verifyWebhookSignature(String rawPayload, String signature);
}
