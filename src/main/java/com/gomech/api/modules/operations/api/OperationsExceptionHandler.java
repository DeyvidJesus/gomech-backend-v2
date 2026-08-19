package com.gomech.api.modules.operations.api;

import com.gomech.api.modules.operations.domain.AppointmentNotFoundException;
import com.gomech.api.modules.operations.domain.CustomerVehicleMismatchException;
import com.gomech.api.modules.operations.domain.InvalidAppointmentStatusTransitionException;
import com.gomech.api.modules.operations.domain.InvalidCalendarRangeException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice(basePackages = "com.gomech.api.modules.operations.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OperationsExceptionHandler {

    private static final String ERROR_TYPE_BASE = "https://gomech.com/docs/errors/";

    @ExceptionHandler(AppointmentNotFoundException.class)
    public ProblemDetail handleAppointmentNotFoundException(AppointmentNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage()
        );
        problemDetail.setTitle("Appointment Not Found");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "appointment-not-found"));
        return problemDetail;
    }

    @ExceptionHandler(CustomerVehicleMismatchException.class)
    public ProblemDetail handleCustomerVehicleMismatchException(CustomerVehicleMismatchException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Customer Vehicle Mismatch");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "customer-vehicle-mismatch"));
        return problemDetail;
    }

    @ExceptionHandler(InvalidAppointmentStatusTransitionException.class)
    public ProblemDetail handleInvalidAppointmentStatusTransitionException(InvalidAppointmentStatusTransitionException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Invalid Status Transition");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "invalid-status-transition"));
        return problemDetail;
    }

    @ExceptionHandler(InvalidCalendarRangeException.class)
    public ProblemDetail handleInvalidCalendarRangeException(InvalidCalendarRangeException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage()
        );
        problemDetail.setTitle("Invalid Calendar Range");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "invalid-calendar-range"));
        return problemDetail;
    }
}
