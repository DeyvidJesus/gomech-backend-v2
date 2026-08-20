package com.gomech.api.modules.tools.api;

import com.gomech.api.modules.tools.domain.InvalidToolOperationException;
import com.gomech.api.modules.tools.domain.ToolCategoryNotFoundException;
import com.gomech.api.modules.tools.domain.ToolNotFoundException;
import com.gomech.api.modules.tools.domain.ToolUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice(basePackages = "com.gomech.api.modules.tools.api")
public class ToolsExceptionHandler {

    @ExceptionHandler(ToolNotFoundException.class)
    public ProblemDetail handleToolNotFound(ToolNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://gomech.com/errors/tool-not-found"));
        pd.setTitle("Ferramenta Não Encontrada");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(ToolCategoryNotFoundException.class)
    public ProblemDetail handleCategoryNotFound(ToolCategoryNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://gomech.com/errors/tool-category-not-found"));
        pd.setTitle("Categoria de Ferramenta Não Encontrada");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(ToolUnavailableException.class)
    public ProblemDetail handleToolUnavailable(ToolUnavailableException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://gomech.com/errors/tool-unavailable"));
        pd.setTitle("Ferramenta Indisponível");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(InvalidToolOperationException.class)
    public ProblemDetail handleInvalidOperation(InvalidToolOperationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("https://gomech.com/errors/invalid-tool-operation"));
        pd.setTitle("Operação Inválida de Ferramenta");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
