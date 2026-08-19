package com.gomech.api.modules.crm.api;

import com.gomech.api.modules.crm.domain.CustomerNotFoundException;
import com.gomech.api.modules.crm.domain.DuplicateDocumentException;
import com.gomech.api.modules.crm.domain.DuplicateLicensePlateException;
import com.gomech.api.modules.crm.domain.InvalidDocumentException;
import com.gomech.api.modules.crm.domain.InvalidLicensePlateException;
import com.gomech.api.modules.crm.domain.VehicleNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice(basePackages = "com.gomech.api.modules.crm.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CrmExceptionHandler {

    private static final String ERROR_TYPE_BASE = "https://gomech.com/docs/errors/";

    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail handleCustomerNotFoundException(CustomerNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage()
        );
        problemDetail.setTitle("Customer Not Found");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "customer-not-found"));
        return problemDetail;
    }

    @ExceptionHandler(VehicleNotFoundException.class)
    public ProblemDetail handleVehicleNotFoundException(VehicleNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage()
        );
        problemDetail.setTitle("Vehicle Not Found");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "vehicle-not-found"));
        return problemDetail;
    }

    @ExceptionHandler(DuplicateDocumentException.class)
    public ProblemDetail handleDuplicateDocumentException(DuplicateDocumentException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage()
        );
        problemDetail.setTitle("Duplicate Document");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "duplicate-document"));
        return problemDetail;
    }

    @ExceptionHandler(DuplicateLicensePlateException.class)
    public ProblemDetail handleDuplicateLicensePlateException(DuplicateLicensePlateException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage()
        );
        problemDetail.setTitle("Duplicate License Plate");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "duplicate-license-plate"));
        return problemDetail;
    }

    @ExceptionHandler(InvalidDocumentException.class)
    public ProblemDetail handleInvalidDocumentException(InvalidDocumentException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Invalid Document");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "invalid-document"));
        return problemDetail;
    }

    @ExceptionHandler(InvalidLicensePlateException.class)
    public ProblemDetail handleInvalidLicensePlateException(InvalidLicensePlateException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problemDetail.setTitle("Invalid License Plate");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "invalid-license-plate"));
        return problemDetail;
    }
}
