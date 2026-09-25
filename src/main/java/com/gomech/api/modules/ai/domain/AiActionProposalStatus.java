package com.gomech.api.modules.ai.domain;

public enum AiActionProposalStatus {
    PENDING("Proposta aguardando revisão e confirmação humana"),
    CONFIRMED("Proposta aprovada pelo operador e enviada para execução"),
    REJECTED("Proposta rejeitada explicitamente pelo operador"),
    EXPIRED("Proposta expirada por ultrapassar a janela de tempo limite (TTL)"),
    EXECUTED("Ação executada com sucesso no domínio de negócio"),
    FAILED("Falha na execução da ação nos comandos de domínio");

    private final String description;

    AiActionProposalStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == REJECTED || this == EXPIRED || this == EXECUTED || this == FAILED;
    }
}
