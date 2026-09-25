package com.gomech.api.modules.ai.domain;

public enum AiCapability {
    DIAGNOSTIC_ASSIST("Diagnóstico e identificação de anomalias veiculares"),
    QUOTE_GENERATION("Proposição automatizada de itens de orçamento e peças"),
    WORK_ORDER_SUMMARY("Sumarização executiva e técnica de ordens de serviço"),
    CUSTOMER_MESSAGE_DRAFT("Redação assistida de comunicados ao cliente"),
    GENERAL_COMPLETION("Processamento e raciocínio técnico genérico");

    private final String description;

    AiCapability(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
