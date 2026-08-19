package com.gomech.api.core.entitlement.domain;

/**
 * Dimensões padronizadas de recursos e cotas gerenciadas pelo Core Entitlement.
 */
public enum QuotaDimension {
    USERS("Quantidade máxima de usuários ativos"),
    UNITS("Quantidade máxima de unidades físicas/filiais"),
    AI_USAGE("Consultas e tokens de inteligência artificial"),
    STORAGE_MB("Armazenamento de anexos e documentos em Megabytes"),
    WHATSAPP_MESSAGES("Envio de notificações e mensagens WhatsApp no ciclo"),
    REPORTS("Geração de relatórios avançados e exportações"),
    MODULE_ACCESS("Acesso a módulos específicos do sistema");

    private final String description;

    QuotaDimension(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
