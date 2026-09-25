package com.gomech.api.modules.analytics.api;

import com.gomech.api.modules.analytics.domain.AnalyticsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice(basePackages = "com.gomech.api.modules.analytics.api")
public class AnalyticsExceptionHandler {

    @ExceptionHandler(AnalyticsException.class)
    public ProblemDetail handleAnalyticsException(AnalyticsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Erro no Processamento de Analytics");
        problem.setType(URI.create("https://gomech.com.br/errors/analytics-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
