package com.gomech.api.modules.ai.domain;

public enum AiActionType {
    APPLY_QUOTE_ITEMS("Aplicação de itens e serviços em orçamento"),
    APPLY_WORK_ORDER_ITEMS("Aplicação de itens e serviços em ordem de serviço"),
    SCHEDULE_PREVENTIVE_APPOINTMENT("Agendamento preventivo de revisão veicular"),
    DRAFT_MESSAGE("Rascunho de comunicado ao cliente"),
    PROPOSE_QUOTE_ITEMS("Proposição estruturada de itens e peças de orçamento"),
    PROPOSE_CHECKLIST("Proposição de checklist de inspeção veicular"),
    NONE("Apenas resposta textual ou diagnóstico sem ação direta");

    private final String description;

    AiActionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
