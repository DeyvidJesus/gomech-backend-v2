package com.gomech.api.modules.analytics.domain;

import java.util.List;

public record KpiDefinition(
        String code,
        String name,
        String description,
        KpiCategory category,
        String formula,
        String unit,
        List<String> dimensions,
        String refreshPolicy
) {}
