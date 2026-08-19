package com.gomech.api.modules.operations.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class AppointmentLifecycleValidator {

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED_TRANSITIONS = Map.of(
            AppointmentStatus.SCHEDULED, Set.of(
                    AppointmentStatus.CONFIRMED,
                    AppointmentStatus.IN_PROGRESS,
                    AppointmentStatus.CANCELED,
                    AppointmentStatus.NO_SHOW
            ),
            AppointmentStatus.CONFIRMED, Set.of(
                    AppointmentStatus.IN_PROGRESS,
                    AppointmentStatus.CANCELED,
                    AppointmentStatus.NO_SHOW
            ),
            AppointmentStatus.IN_PROGRESS, Set.of(
                    AppointmentStatus.COMPLETED,
                    AppointmentStatus.CANCELED
            ),
            AppointmentStatus.COMPLETED, Collections.emptySet(),
            AppointmentStatus.CANCELED, Collections.emptySet(),
            AppointmentStatus.NO_SHOW, Collections.emptySet()
    );

    private AppointmentLifecycleValidator() {
    }

    public static boolean canTransition(AppointmentStatus currentStatus, AppointmentStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        if (currentStatus == targetStatus) {
            return true;
        }
        Set<AppointmentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
        return allowed.contains(targetStatus);
    }

    public static void validateTransition(AppointmentStatus currentStatus, AppointmentStatus targetStatus) {
        if (!canTransition(currentStatus, targetStatus)) {
            throw new InvalidAppointmentStatusTransitionException(currentStatus, targetStatus);
        }
    }
}
