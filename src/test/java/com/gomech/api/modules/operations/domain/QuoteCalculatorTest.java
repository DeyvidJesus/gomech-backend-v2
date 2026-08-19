package com.gomech.api.modules.operations.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuoteCalculatorTest {

    @Test
    @DisplayName("Deve calcular item com sucesso sem desconto e sem imposto")
    void shouldCalculateItemWithoutDiscountAndTax() {
        QuoteCalculator.CalculatedItem item = QuoteCalculator.calculateItem(
                new BigDecimal("2.00"),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                QuoteItemType.PART
        );

        assertEquals(new BigDecimal("2.00"), item.quantity());
        assertEquals(new BigDecimal("150.00"), item.unitPrice());
        assertEquals(new BigDecimal("0.00"), item.discountAmount());
        assertEquals(new BigDecimal("0.00"), item.taxAmount());
        assertEquals(new BigDecimal("300.00"), item.totalAmount());
        assertEquals(QuoteItemType.PART, item.type());
    }

    @Test
    @DisplayName("Deve calcular item com desconto e imposto corretamente com arredondamento HALF_UP")
    void shouldCalculateItemWithDiscountAndTax() {
        // Quantidade: 4, Unitário: 50.00 -> Bruto: 200.00
        // Desconto: 20.00 -> Líquido: 180.00
        // Imposto: 5% -> 9.00
        // Total: 189.00
        QuoteCalculator.CalculatedItem item = QuoteCalculator.calculateItem(
                new BigDecimal("4.00"),
                new BigDecimal("50.00"),
                new BigDecimal("20.00"),
                new BigDecimal("5.00"),
                QuoteItemType.LABOR
        );

        assertEquals(new BigDecimal("20.00"), item.discountAmount());
        assertEquals(new BigDecimal("9.00"), item.taxAmount());
        assertEquals(new BigDecimal("189.00"), item.totalAmount());
        assertEquals(QuoteItemType.LABOR, item.type());
    }

    @Test
    @DisplayName("Deve rejeitar quantidade zero ou negativa")
    void shouldRejectZeroOrNegativeQuantity() {
        assertThrows(InvalidMonetaryAmountException.class, () ->
                QuoteCalculator.calculateItem(BigDecimal.ZERO, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, QuoteItemType.PART)
        );

        assertThrows(InvalidMonetaryAmountException.class, () ->
                QuoteCalculator.calculateItem(new BigDecimal("-1.00"), new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, QuoteItemType.PART)
        );
    }

    @Test
    @DisplayName("Deve rejeitar valor unitário negativo")
    void shouldRejectNegativeUnitPrice() {
        assertThrows(InvalidMonetaryAmountException.class, () ->
                QuoteCalculator.calculateItem(BigDecimal.ONE, new BigDecimal("-10.00"), BigDecimal.ZERO, BigDecimal.ZERO, QuoteItemType.PART)
        );
    }

    @Test
    @DisplayName("Deve rejeitar desconto negativo ou maior que o valor bruto")
    void shouldRejectInvalidDiscount() {
        assertThrows(InvalidMonetaryAmountException.class, () ->
                QuoteCalculator.calculateItem(BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("-5.00"), BigDecimal.ZERO, QuoteItemType.PART)
        );

        assertThrows(InvalidMonetaryAmountException.class, () ->
                QuoteCalculator.calculateItem(BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("150.00"), BigDecimal.ZERO, QuoteItemType.PART)
        );
    }

    @Test
    @DisplayName("Deve rejeitar alíquota de imposto negativa ou superior a 100%")
    void shouldRejectInvalidTaxRate() {
        assertThrows(InvalidMonetaryAmountException.class, () ->
                QuoteCalculator.calculateItem(BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("-1.00"), QuoteItemType.PART)
        );

        assertThrows(InvalidMonetaryAmountException.class, () ->
                QuoteCalculator.calculateItem(BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("101.00"), QuoteItemType.PART)
        );
    }

    @Test
    @DisplayName("Deve calcular totais do orçamento separando peças e mão de obra")
    void shouldCalculateTotalsCorrectly() {
        // Item 1: Peça - Qtd 2, Preço 100, Desc 10, Tax 0% -> Total: 190.00 (Bruto 200)
        QuoteCalculator.CalculatedItem partItem = QuoteCalculator.calculateItem(
                new BigDecimal("2.00"),
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                QuoteItemType.PART
        );

        // Item 2: Mão de obra - Qtd 3, Preço 80, Desc 0, Tax 10% -> Total: 264.00 (Bruto 240, Tax 24)
        QuoteCalculator.CalculatedItem laborItem = QuoteCalculator.calculateItem(
                new BigDecimal("3.00"),
                new BigDecimal("80.00"),
                BigDecimal.ZERO,
                new BigDecimal("10.00"),
                QuoteItemType.LABOR
        );

        QuoteCalculator.CalculatedTotals totals = QuoteCalculator.calculateTotals(List.of(partItem, laborItem));

        assertEquals(new BigDecimal("440.00"), totals.subtotalAmount()); // 200 + 240
        assertEquals(new BigDecimal("10.00"), totals.discountAmount());
        assertEquals(new BigDecimal("24.00"), totals.taxAmount());
        assertEquals(new BigDecimal("190.00"), totals.totalPartsAmount());
        assertEquals(new BigDecimal("264.00"), totals.totalLaborAmount());
        assertEquals(new BigDecimal("454.00"), totals.totalAmount()); // 440 - 10 + 24 = 454
    }

    @Test
    @DisplayName("Deve retornar totais zerados para lista nula ou vazia")
    void shouldReturnZeroTotalsForEmptyList() {
        QuoteCalculator.CalculatedTotals totals = QuoteCalculator.calculateTotals(List.of());

        assertEquals(new BigDecimal("0.00"), totals.subtotalAmount());
        assertEquals(new BigDecimal("0.00"), totals.discountAmount());
        assertEquals(new BigDecimal("0.00"), totals.taxAmount());
        assertEquals(new BigDecimal("0.00"), totals.totalLaborAmount());
        assertEquals(new BigDecimal("0.00"), totals.totalPartsAmount());
        assertEquals(new BigDecimal("0.00"), totals.totalAmount());
    }
}
