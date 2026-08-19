package com.gomech.api.modules.operations.api;

import com.gomech.api.modules.operations.domain.AppointmentNotFoundException;
import com.gomech.api.modules.operations.domain.CustomerVehicleMismatchException;
import com.gomech.api.modules.operations.domain.InspectionAlreadyCompletedException;
import com.gomech.api.modules.operations.domain.InspectionNotFoundException;
import com.gomech.api.modules.operations.domain.InvalidAppointmentInspectionLinkException;
import com.gomech.api.modules.operations.domain.InvalidAppointmentStatusTransitionException;
import com.gomech.api.modules.operations.domain.InvalidCalendarRangeException;
import com.gomech.api.modules.operations.domain.InvalidInspectionStatusTransitionException;
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

    @ExceptionHandler(InspectionNotFoundException.class)
    public ProblemDetail handleInspectionNotFoundException(InspectionNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage()
        );
        problemDetail.setTitle("Inspection Not Found");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "inspection-not-found"));
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

    @ExceptionHandler(InvalidInspectionStatusTransitionException.class)
    public ProblemDetail handleInvalidInspectionStatusTransitionException(InvalidInspectionStatusTransitionException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Invalid Inspection Status Transition");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "invalid-inspection-status-transition"));
        return problemDetail;
    }

    @ExceptionHandler(InspectionAlreadyCompletedException.class)
    public ProblemDetail handleInspectionAlreadyCompletedException(InspectionAlreadyCompletedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Inspection Already Finalized");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "inspection-already-finalized"));
        return problemDetail;
    }

    @ExceptionHandler(InvalidAppointmentInspectionLinkException.class)
    public ProblemDetail handleInvalidAppointmentInspectionLinkException(InvalidAppointmentInspectionLinkException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Invalid Appointment Link");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "invalid-appointment-link"));
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

    @ExceptionHandler(com.gomech.api.modules.operations.domain.QuoteNotFoundException.class)
    public ProblemDetail handleQuoteNotFoundException(com.gomech.api.modules.operations.domain.QuoteNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage()
        );
        problemDetail.setTitle("Quote Not Found");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "quote-not-found"));
        return problemDetail;
    }

    @ExceptionHandler(com.gomech.api.modules.operations.domain.InvalidQuoteStatusTransitionException.class)
    public ProblemDetail handleInvalidQuoteStatusTransitionException(com.gomech.api.modules.operations.domain.InvalidQuoteStatusTransitionException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Invalid Quote Status Transition");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "invalid-quote-status-transition"));
        return problemDetail;
    }

    @ExceptionHandler(com.gomech.api.modules.operations.domain.QuoteCannotBeModifiedException.class)
    public ProblemDetail handleQuoteCannotBeModifiedException(com.gomech.api.modules.operations.domain.QuoteCannotBeModifiedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Quote Cannot Be Modified");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "quote-cannot-be-modified"));
        return problemDetail;
    }

    @ExceptionHandler(com.gomech.api.modules.operations.domain.QuoteNotApprovedForSendingException.class)
    public ProblemDetail handleQuoteNotApprovedForSendingException(com.gomech.api.modules.operations.domain.QuoteNotApprovedForSendingException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Quote Not Approved For Sending");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "quote-not-approved-for-sending"));
        return problemDetail;
    }

    @ExceptionHandler(com.gomech.api.modules.operations.domain.InvalidMonetaryAmountException.class)
    public ProblemDetail handleInvalidMonetaryAmountException(com.gomech.api.modules.operations.domain.InvalidMonetaryAmountException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Invalid Monetary Amount");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "invalid-monetary-amount"));
        return problemDetail;
    }

    @ExceptionHandler(com.gomech.api.modules.operations.domain.WorkOrderNotFoundException.class)
    public ProblemDetail handleWorkOrderNotFoundException(com.gomech.api.modules.operations.domain.WorkOrderNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage()
        );
        problemDetail.setTitle("Work Order Not Found");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "work-order-not-found"));
        return problemDetail;
    }

    @ExceptionHandler(com.gomech.api.modules.operations.domain.InvalidWorkOrderStatusTransitionException.class)
    public ProblemDetail handleInvalidWorkOrderStatusTransitionException(com.gomech.api.modules.operations.domain.InvalidWorkOrderStatusTransitionException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Invalid Work Order Status Transition");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "invalid-work-order-status-transition"));
        return problemDetail;
    }

    @ExceptionHandler(com.gomech.api.modules.operations.domain.WorkOrderAlreadyCompletedException.class)
    public ProblemDetail handleWorkOrderAlreadyCompletedException(com.gomech.api.modules.operations.domain.WorkOrderAlreadyCompletedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Work Order Already Completed");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "work-order-already-completed"));
        return problemDetail;
    }

    @ExceptionHandler(com.gomech.api.modules.operations.domain.QuoteAlreadyConvertedException.class)
    public ProblemDetail handleQuoteAlreadyConvertedException(com.gomech.api.modules.operations.domain.QuoteAlreadyConvertedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Quote Already Converted");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "quote-already-converted"));
        return problemDetail;
    }

    @ExceptionHandler(com.gomech.api.modules.operations.domain.QuoteNotEligibleForWorkOrderException.class)
    public ProblemDetail handleQuoteNotEligibleForWorkOrderException(com.gomech.api.modules.operations.domain.QuoteNotEligibleForWorkOrderException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Quote Not Eligible For Work Order");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "quote-not-eligible-for-work-order"));
        return problemDetail;
    }
}
