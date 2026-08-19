package com.gomech.api.modules.operations.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class WorkOrderLifecycleValidator {

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> VALID_TRANSITIONS;

    static {
        Map<WorkOrderStatus, Set<WorkOrderStatus>> map = new EnumMap<>(WorkOrderStatus.class);

        map.put(WorkOrderStatus.DRAFT, EnumSet.of(
                WorkOrderStatus.OPEN,
                WorkOrderStatus.CANCELED
        ));

        map.put(WorkOrderStatus.OPEN, EnumSet.of(
                WorkOrderStatus.IN_PROGRESS,
                WorkOrderStatus.WAITING_PARTS,
                WorkOrderStatus.WAITING_CUSTOMER,
                WorkOrderStatus.CANCELED
        ));

        map.put(WorkOrderStatus.IN_PROGRESS, EnumSet.of(
                WorkOrderStatus.WAITING_PARTS,
                WorkOrderStatus.WAITING_CUSTOMER,
                WorkOrderStatus.COMPLETED,
                WorkOrderStatus.CANCELED
        ));

        map.put(WorkOrderStatus.WAITING_PARTS, EnumSet.of(
                WorkOrderStatus.IN_PROGRESS,
                WorkOrderStatus.WAITING_CUSTOMER,
                WorkOrderStatus.CANCELED
        ));

        map.put(WorkOrderStatus.WAITING_CUSTOMER, EnumSet.of(
                WorkOrderStatus.IN_PROGRESS,
                WorkOrderStatus.WAITING_PARTS,
                WorkOrderStatus.CANCELED
        ));

        // Terminal states
        map.put(WorkOrderStatus.COMPLETED, Collections.emptySet());
        map.put(WorkOrderStatus.CANCELED, Collections.emptySet());

        VALID_TRANSITIONS = Collections.unmodifiableMap(map);
    }

    private WorkOrderLifecycleValidator() {
    }

    public static void validateTransition(WorkOrderStatus currentStatus, WorkOrderStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return;
        }

        Set<WorkOrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
        if (!allowed.contains(targetStatus)) {
            throw new InvalidWorkOrderStatusTransitionException(currentStatus, targetStatus);
        }
    }

    public static boolean isTerminal(WorkOrderStatus status) {
        return status == WorkOrderStatus.COMPLETED || status == WorkOrderStatus.CANCELED;
    }

    public static boolean canModifyItems(WorkOrderStatus status) {
        return status != WorkOrderStatus.COMPLETED && status != WorkOrderStatus.CANCELED;
    }
}
