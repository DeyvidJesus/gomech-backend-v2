package com.gomech.api.modules.operations.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WorkOrderKanbanResponse(
        UUID unitId,
        int totalActiveOrders,
        BigDecimal totalActiveAmount,
        List<KanbanColumnResponse> columns
) {
}
