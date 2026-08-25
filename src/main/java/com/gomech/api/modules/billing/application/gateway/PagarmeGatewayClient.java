package com.gomech.api.modules.billing.application.gateway;

import com.gomech.api.modules.billing.infrastructure.gateway.PagarmeDto;

import java.util.List;

public interface PagarmeGatewayClient {

    /**
     * Cria um Payment Link oficial do tipo 'subscription' no Pagar.me V5 para Checkout Hospedado.
     */
    PagarmeDto.PaymentLinkResponse createHostedCheckout(PagarmeDto.CreateHostedCheckoutRequest request);

    /**
     * Cria ou obtém cliente (Customer) no Pagar.me V5.
     */
    PagarmeDto.CustomerResponse createOrGetCustomer(PagarmeDto.CustomerRequest request);

    /**
     * Lista planos oficiais cadastrados no Pagar.me V5.
     */
    List<PagarmeDto.PagarmePlanDto> listPlans();

    /**
     * Obtém detalhes de um plano no Pagar.me V5.
     */
    PagarmeDto.PagarmePlanDto getPlan(String planId);

    /**
     * Cancela uma assinatura recorrente no Pagar.me V5.
     */
    boolean cancelSubscription(String gatewaySubscriptionId);

    /**
     * Valida a assinatura criptográfica do Webhook do Pagar.me.
     */
    boolean verifyWebhookSignature(String rawPayload, String signature);
}
