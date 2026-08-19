package com.gomech.api.core.entitlement.domain;

/**
 * Lançada quando o Tenant tenta acessar um módulo de negócio não incluso em seu plano de assinatura.
 */
public class ModuleAccessDeniedException extends RuntimeException {

    private final String moduleCode;

    public ModuleAccessDeniedException(String moduleCode, String message) {
        super(message);
        this.moduleCode = moduleCode;
    }

    public String getModuleCode() {
        return moduleCode;
    }
}
