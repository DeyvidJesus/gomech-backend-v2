package com.gomech.api.modules.operations.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class InspectionLifecycleValidator {

    private static final Map<InspectionStatus, Set<InspectionStatus>> ALLOWED_TRANSITIONS = Map.of(
            InspectionStatus.IN_PROGRESS, Set.of(
                    InspectionStatus.COMPLETED,
                    InspectionStatus.CANCELED
            ),
            InspectionStatus.COMPLETED, Collections.emptySet(),
            InspectionStatus.CANCELED, Collections.emptySet()
    );

    private InspectionLifecycleValidator() {
    }

    public static boolean canTransition(InspectionStatus currentStatus, InspectionStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        if (currentStatus == targetStatus) {
            return true;
        }
        Set<InspectionStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
        return allowed.contains(targetStatus);
    }

    public static void validateTransition(InspectionStatus currentStatus, InspectionStatus targetStatus) {
        if (!canTransition(currentStatus, targetStatus)) {
            throw new InvalidInspectionStatusTransitionException(currentStatus, targetStatus);
        }
    }
}
