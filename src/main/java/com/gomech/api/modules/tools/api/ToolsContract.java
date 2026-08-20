package com.gomech.api.modules.tools.api;

import java.util.UUID;

public interface ToolsContract {

    /**
     * Records checkout and usage of a tool for a specific work order.
     */
    void recordWorkOrderToolUsage(UUID toolId, UUID workOrderId, UUID mechanicUserId, UUID tenantId);

    /**
     * Concludes all active tool usages associated with a finished work order.
     */
    void releaseWorkOrderTools(UUID workOrderId, UUID tenantId);

    /**
     * Checks if a tool is available for checkout in a specific unit.
     */
    boolean isToolAvailable(UUID toolId, UUID tenantId);
}
