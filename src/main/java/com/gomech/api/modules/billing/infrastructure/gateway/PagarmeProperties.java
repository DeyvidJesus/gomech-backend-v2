package com.gomech.api.modules.billing.infrastructure.gateway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pagarme")
@Getter
@Setter
public class PagarmeProperties {

    /**
     * Chave de API Pagar.me (sk_test_... ou sk_...)
     */
    private String apiKey = "sk_test_gomech_placeholder";

    /**
     * URL base da API Pagar.me v5
     */
    private String apiUrl = "https://api.pagar.me/core/v5";

    /**
     * Chave secreta de validação de assinatura do Webhook
     */
    private String webhookSecret = "whsec_gomech_placeholder";

    /**
     * Ativa simulação local caso esteja em ambiente de teste ou sem credenciais ativas
     */
    private boolean mockEnabled = true;
}
