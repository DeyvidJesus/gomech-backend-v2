package com.gomech.api.core.exceptions;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_TYPE_BASE = "https://gomech.com/docs/errors/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "Input validation failed for some parameters."
        );
        problemDetail.setTitle("Validation Failed");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "validation-failed"));

        List<InvalidParam> invalidParams = ex.getBindingResult().getAllErrors().stream()
                .map(GlobalExceptionHandler::toInvalidParam)
                .toList();

        problemDetail.setProperty("invalidParams", invalidParams);
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage()
        );
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "bad-request"));
        return problemDetail;
    }

    @ExceptionHandler(SecurityException.class)
    public ProblemDetail handleSecurityException(SecurityException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, ex.getMessage()
        );
        problemDetail.setTitle("Unauthorized");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "unauthorized"));
        return problemDetail;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailureException(OptimisticLockingFailureException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The resource was modified by another concurrent transaction. Please refresh and retry."
        );
        problemDetail.setTitle("Conflict");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "concurrency-conflict"));
        return problemDetail;
    }

    @ExceptionHandler(com.gomech.api.core.entitlement.domain.QuotaExceededException.class)
    public ProblemDetail handleQuotaExceededException(com.gomech.api.core.entitlement.domain.QuotaExceededException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYMENT_REQUIRED, ex.getMessage()
        );
        problemDetail.setTitle("Quota Exceeded");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "quota-exceeded"));
        problemDetail.setProperty("dimension", ex.getDimension().name());
        problemDetail.setProperty("currentUsage", ex.getCurrentUsage());
        problemDetail.setProperty("limit", ex.getLimit());
        return problemDetail;
    }

    @ExceptionHandler(com.gomech.api.core.entitlement.domain.ModuleAccessDeniedException.class)
    public ProblemDetail handleModuleAccessDeniedException(com.gomech.api.core.entitlement.domain.ModuleAccessDeniedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, ex.getMessage()
        );
        problemDetail.setTitle("Module Access Denied");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "module-access-denied"));
        problemDetail.setProperty("moduleCode", ex.getModuleCode());
        return problemDetail;
    }

    private static InvalidParam toInvalidParam(ObjectError error) {
        String name = error instanceof FieldError fieldError
                ? fieldError.getField()
                : error.getObjectName();
        return new InvalidParam(name, error.getDefaultMessage());
    }

    /**
     * RFC 7807 extension member documented by ADR-004: REST API Conventions.
     * Serialized as an array so several problems on the same field stay representable.
     */
    public record InvalidParam(String name, String reason) {}
}
