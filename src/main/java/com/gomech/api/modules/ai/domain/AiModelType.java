package com.gomech.api.modules.ai.domain;

public enum AiModelType {
    FAST_TURBO("gomech-turbo-fast", "Modelo rápido de baixa latência"),
    REASONING_PRO("gomech-reasoning-pro", "Modelo avançado de alta precisão e raciocínio técnico"),
    VISION_MULTIMODAL("gomech-vision-pro", "Modelo multimodal para análise de fotos e inspeções");

    private final String modelName;
    private final String description;

    AiModelType(String modelName, String description) {
        this.modelName = modelName;
        this.description = description;
    }

    public String getModelName() {
        return modelName;
    }

    public String getDescription() {
        return description;
    }
}
