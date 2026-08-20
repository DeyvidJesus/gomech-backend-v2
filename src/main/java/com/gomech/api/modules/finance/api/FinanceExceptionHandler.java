package com.gomech.api.modules.finance.api;

import com.gomech.api.modules.finance.domain.AccountNotFoundException;
import com.gomech.api.modules.finance.domain.FinanceCategoryNotFoundException;
import com.gomech.api.modules.finance.domain.InvalidFinanceOperationException;
import com.gomech.api.modules.finance.domain.PayableNotFoundException;
import com.gomech.api.modules.finance.domain.ReceivableNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice(basePackages = "com.gomech.api.modules.finance.api")
public class FinanceExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://gomech.com/errors/account-not-found"));
        pd.setTitle("Conta Financeira Não Encontrada");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(ReceivableNotFoundException.class)
    public ProblemDetail handleReceivableNotFound(ReceivableNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://gomech.com/errors/receivable-not-found"));
        pd.setTitle("Conta a Receber Não Encontrada");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(PayableNotFoundException.class)
    public ProblemDetail handlePayableNotFound(PayableNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://gomech.com/errors/payable-not-found"));
        pd.setTitle("Conta a Pagar Não Encontrada");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(FinanceCategoryNotFoundException.class)
    public ProblemDetail handleCategoryNotFound(FinanceCategoryNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://gomech.com/errors/finance-category-not-found"));
        pd.setTitle("Categoria Financeira Não Encontrada");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(InvalidFinanceOperationException.class)
    public ProblemDetail handleInvalidOperation(InvalidFinanceOperationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("https://gomech.com/errors/invalid-finance-operation"));
        pd.setTitle("Operação Financeira Inválida");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
