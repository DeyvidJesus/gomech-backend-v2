package com.gomech.api.modules.operations.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class WorkOrderCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private WorkOrderCalculator() {
    }

    public record CalculatedItem(
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            WorkOrderItemType type
    ) {
    }

    public record CalculatedTotals(
            BigDecimal subtotalAmount,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal totalServicesAmount,
            BigDecimal totalPartsAmount,
            BigDecimal totalAmount
    ) {
    }

    public static CalculatedItem calculateItem(
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal taxRate,
            WorkOrderItemType type
    ) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidMonetaryAmountException("A quantidade do item deve ser maior que zero.");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidMonetaryAmountException("O preço unitário não pode ser negativo.");
        }

        BigDecimal safeDiscount = discountAmount != null ? discountAmount.setScale(SCALE, ROUNDING_MODE) : BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        BigDecimal safeTaxRate = taxRate != null ? taxRate.setScale(SCALE, ROUNDING_MODE) : BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);

        if (safeDiscount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidMonetaryAmountException("O valor do desconto não pode ser negativo.");
        }
        if (safeTaxRate.compareTo(BigDecimal.ZERO) < 0 || safeTaxRate.compareTo(ONE_HUNDRED) > 0) {
            throw new InvalidMonetaryAmountException("A alíquota de imposto deve estar entre 0% e 100%.");
        }

        BigDecimal grossAmount = quantity.multiply(unitPrice).setScale(SCALE, ROUNDING_MODE);

        if (safeDiscount.compareTo(grossAmount) > 0) {
            throw new InvalidMonetaryAmountException("O desconto não pode ser maior que o valor bruto do item.");
        }

        BigDecimal netAmount = grossAmount.subtract(safeDiscount).setScale(SCALE, ROUNDING_MODE);
        BigDecimal taxAmount = netAmount.multiply(safeTaxRate).divide(ONE_HUNDRED, SCALE, ROUNDING_MODE);
        BigDecimal totalAmount = netAmount.add(taxAmount).setScale(SCALE, ROUNDING_MODE);

        return new CalculatedItem(
                quantity.setScale(SCALE, ROUNDING_MODE),
                unitPrice.setScale(SCALE, ROUNDING_MODE),
                safeDiscount,
                safeTaxRate,
                taxAmount,
                totalAmount,
                type
        );
    }

    public static CalculatedTotals calculateTotals(List<CalculatedItem> items) {
        if (items == null || items.isEmpty()) {
            return new CalculatedTotals(
                    BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE),
                    BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE),
                    BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE),
                    BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE),
                    BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE),
                    BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE)
            );
        }

        BigDecimal subtotal = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        BigDecimal totalDiscount = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        BigDecimal totalTax = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        BigDecimal totalServices = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        BigDecimal totalParts = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);

        for (CalculatedItem item : items) {
            BigDecimal gross = item.quantity().multiply(item.unitPrice()).setScale(SCALE, ROUNDING_MODE);
            subtotal = subtotal.add(gross);
            totalDiscount = totalDiscount.add(item.discountAmount());
            totalTax = totalTax.add(item.taxAmount());

            if (item.type() == WorkOrderItemType.SERVICE) {
                totalServices = totalServices.add(item.totalAmount());
            } else if (item.type() == WorkOrderItemType.PART) {
                totalParts = totalParts.add(item.totalAmount());
            }
        }

        BigDecimal grandTotal = subtotal.subtract(totalDiscount).add(totalTax).setScale(SCALE, ROUNDING_MODE);

        return new CalculatedTotals(
                subtotal,
                totalDiscount,
                totalTax,
                totalServices,
                totalParts,
                grandTotal
        );
    }
}
