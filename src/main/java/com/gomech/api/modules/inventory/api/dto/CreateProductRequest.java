package com.gomech.api.modules.inventory.api.dto;

import com.gomech.api.modules.inventory.domain.UnitOfMeasure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductRequest(
    UUID unitId,
    UUID supplierId,

    @NotBlank(message = "O código SKU é obrigatório")
    @Size(max = 100, message = "O código SKU deve ter no máximo 100 caracteres")
    String skuCode,

    @NotBlank(message = "O nome do produto é obrigatório")
    @Size(max = 255, message = "O nome do produto deve ter no máximo 255 caracteres")
    String name,

    String category,
    String barcode,
    String brand,

    @NotNull(message = "A unidade de medida é obrigatória")
    UnitOfMeasure unitOfMeasure,

    @NotNull(message = "O preço de custo é obrigatório")
    @PositiveOrZero(message = "O preço de custo deve ser maior ou igual a zero")
    BigDecimal costPrice,

    @NotNull(message = "O preço de venda é obrigatório")
    @PositiveOrZero(message = "O preço de venda deve ser maior ou igual a zero")
    BigDecimal sellingPrice,

    @PositiveOrZero(message = "O estoque mínimo deve ser maior ou igual a zero")
    Integer minStock,

    String locationInWarehouse,

    BigDecimal initialStockQuantity,
    UUID initialStockUnitId
) {}
