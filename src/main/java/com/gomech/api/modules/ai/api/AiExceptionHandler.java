package com.gomech.api.modules.ai.api;

import com.gomech.api.modules.ai.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice(basePackages = "com.gomech.api.modules.ai.api")
public class AiExceptionHandler {

    @ExceptionHandler(AiQuotaExceededException.class)
    public ResponseEntity<ProblemDetail> handleQuotaExceeded(AiQuotaExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
        problem.setTitle("Cota de IA Excedida");
        problem.setType(URI.create("https://gomech.com.br/errors/ai-quota-exceeded"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(problem);
    }

    @ExceptionHandler(AiRateLimitException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(AiRateLimitException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setTitle("Limite de Taxa do AI Gateway Atingido");
        problem.setType(URI.create("https://gomech.com.br/errors/ai-rate-limit-exceeded"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
        problem.setProperty("timestamp", Instant.now());

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        return new ResponseEntity<>(problem, headers, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleServiceUnavailable(AiServiceUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Serviço de IA Indisponível");
        problem.setType(URI.create("https://gomech.com.br/errors/ai-service-unavailable"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(AiActionExpiredException.class)
    public ResponseEntity<ProblemDetail> handleActionExpired(AiActionExpiredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        problem.setTitle("Proposta de Ação Expirada");
        problem.setType(URI.create("https://gomech.com.br/errors/ai-action-expired"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.GONE).body(problem);
    }

    @ExceptionHandler(AiActionAlreadyProcessedException.class)
    public ResponseEntity<ProblemDetail> handleActionAlreadyProcessed(AiActionAlreadyProcessedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Proposta de Ação Já Processada");
        problem.setType(URI.create("https://gomech.com.br/errors/ai-action-already-processed"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(AiException.class)
    public ResponseEntity<ProblemDetail> handleAiGeneralException(AiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Erro no AI Gateway");
        problem.setType(URI.create("https://gomech.com.br/errors/ai-gateway-error"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
