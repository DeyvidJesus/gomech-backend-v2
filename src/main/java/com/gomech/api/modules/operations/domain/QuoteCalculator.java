package com.gomech.api.modules.operations.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class QuoteCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private QuoteCalculator() {
    }

    public record CalculatedItem(
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            QuoteItemType type
    ) {
    }

    public record CalculatedTotals(
            BigDecimal subtotalAmount,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal totalLaborAmount,
            BigDecimal totalPartsAmount,
            BigDecimal totalAmount
    ) {
    }

    public static CalculatedItem calculateItem(
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal taxRate,
            QuoteItemType type
    ) {
        BigDecimal safeQuantity = quantity != null ? quantity.setScale(SCALE, ROUNDING) : BigDecimal.ONE.setScale(SCALE, ROUNDING);
        BigDecimal safeUnitPrice = unitPrice != null ? unitPrice.setScale(SCALE, ROUNDING) : BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal safeDiscount = discountAmount != null ? discountAmount.setScale(SCALE, ROUNDING) : BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal safeTaxRate = taxRate != null ? taxRate.setScale(SCALE, ROUNDING) : BigDecimal.ZERO.setScale(SCALE, ROUNDING);

        if (safeQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidMonetaryAmountException("A quantidade do item deve ser maior que zero.");
        }
        if (safeUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidMonetaryAmountException("O valor unitário do item não pode ser negativo.");
        }
        if (safeDiscount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidMonetaryAmountException("O desconto do item não pode ser negativo.");
        }
        if (safeTaxRate.compareTo(BigDecimal.ZERO) < 0 || safeTaxRate.compareTo(ONE_HUNDRED) > 0) {
            throw new InvalidMonetaryAmountException("A alíquota de imposto deve estar entre 0% e 100%.");
        }

        BigDecimal gross = safeQuantity.multiply(safeUnitPrice).setScale(SCALE, ROUNDING);
        if (safeDiscount.compareTo(gross) > 0) {
            throw new InvalidMonetaryAmountException("O desconto não pode exceder o valor bruto do item (" + gross + ").");
        }

        BigDecimal net = gross.subtract(safeDiscount).setScale(SCALE, ROUNDING);
        BigDecimal calculatedTaxAmount = net.multiply(safeTaxRate).divide(ONE_HUNDRED, SCALE, ROUNDING);
        BigDecimal total = net.add(calculatedTaxAmount).setScale(SCALE, ROUNDING);

        return new CalculatedItem(
                safeQuantity,
                safeUnitPrice,
                safeDiscount,
                safeTaxRate,
                calculatedTaxAmount,
                total,
                type != null ? type : QuoteItemType.PART
        );
    }

    public static CalculatedTotals calculateTotals(List<CalculatedItem> items) {
        if (items == null || items.isEmpty()) {
            return new CalculatedTotals(
                    BigDecimal.ZERO.setScale(SCALE, ROUNDING),
                    BigDecimal.ZERO.setScale(SCALE, ROUNDING),
                    BigDecimal.ZERO.setScale(SCALE, ROUNDING),
                    BigDecimal.ZERO.setScale(SCALE, ROUNDING),
                    BigDecimal.ZERO.setScale(SCALE, ROUNDING),
                    BigDecimal.ZERO.setScale(SCALE, ROUNDING)
            );
        }

        BigDecimal subtotal = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal totalDiscount = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal totalTax = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal totalLabor = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal totalParts = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal grandTotal = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

        for (CalculatedItem item : items) {
            BigDecimal gross = item.quantity().multiply(item.unitPrice()).setScale(SCALE, ROUNDING);
            subtotal = subtotal.add(gross);
            totalDiscount = totalDiscount.add(item.discountAmount());
            totalTax = totalTax.add(item.taxAmount());
            grandTotal = grandTotal.add(item.totalAmount());

            if (item.type() == QuoteItemType.LABOR) {
                totalLabor = totalLabor.add(item.totalAmount());
            } else {
                totalParts = totalParts.add(item.totalAmount());
            }
        }

        return new CalculatedTotals(
                subtotal.setScale(SCALE, ROUNDING),
                totalDiscount.setScale(SCALE, ROUNDING),
                totalTax.setScale(SCALE, ROUNDING),
                totalLabor.setScale(SCALE, ROUNDING),
                totalParts.setScale(SCALE, ROUNDING),
                grandTotal.setScale(SCALE, ROUNDING)
        );
    }
}
