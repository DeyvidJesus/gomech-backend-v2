package com.gomech.api.modules.operations.api.dto;

import com.gomech.api.modules.operations.domain.WorkOrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record KanbanColumnResponse(
        WorkOrderStatus status,
        String title,
        int totalOrders,
        BigDecimal totalAmount,
        List<WorkOrderSummaryResponse> orders
) {
}
