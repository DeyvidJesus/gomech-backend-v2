package com.gomech.api.modules.inventory.api;

import com.gomech.api.modules.inventory.domain.InsufficientStockException;
import com.gomech.api.modules.inventory.domain.InvalidStockTransferException;
import com.gomech.api.modules.inventory.domain.ProductNotFoundException;
import com.gomech.api.modules.inventory.domain.StockReservationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice(basePackages = "com.gomech.api.modules.inventory")
public class InventoryExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Product Not Found");
        problem.setType(URI.create("https://gomech.com/docs/errors/product-not-found"));
        return problem;
    }

    @ExceptionHandler(StockReservationNotFoundException.class)
    public ProblemDetail handleStockReservationNotFound(StockReservationNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Stock Reservation Not Found");
        problem.setType(URI.create("https://gomech.com/docs/errors/stock-reservation-not-found"));
        return problem;
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Insufficient Stock");
        problem.setType(URI.create("https://gomech.com/docs/errors/insufficient-stock"));
        problem.setProperty("productId", ex.getProductId());
        problem.setProperty("requested", ex.getRequested());
        problem.setProperty("available", ex.getAvailable());
        return problem;
    }

    @ExceptionHandler(InvalidStockTransferException.class)
    public ProblemDetail handleInvalidStockTransfer(InvalidStockTransferException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Invalid Stock Transfer");
        problem.setType(URI.create("https://gomech.com/docs/errors/invalid-stock-transfer"));
        return problem;
    }
}
