package com.gomech.api.modules.operations.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkOrderCalculatorTest {

    @Test
    @DisplayName("Deve calcular item de peça com quantidade, preço unitário, desconto e imposto")
    void shouldCalculatePartItemCorrectly() {
        // Quantidade: 2, Preço: 150.00 -> Bruto: 300.00
        // Desconto: 20.00 -> Líquido: 280.00
        // Imposto: 10% -> 28.00
        // Total: 308.00
        WorkOrderCalculator.CalculatedItem item = WorkOrderCalculator.calculateItem(
                new BigDecimal("2.00"),
                new BigDecimal("150.00"),
                new BigDecimal("20.00"),
                new BigDecimal("10.00"),
                WorkOrderItemType.PART
        );

        assertEquals(new BigDecimal("2.00"), item.quantity());
        assertEquals(new BigDecimal("150.00"), item.unitPrice());
        assertEquals(new BigDecimal("20.00"), item.discountAmount());
        assertEquals(new BigDecimal("10.00"), item.taxRate());
        assertEquals(new BigDecimal("28.00"), item.taxAmount());
        assertEquals(new BigDecimal("308.00"), item.totalAmount());
        assertEquals(WorkOrderItemType.PART, item.type());
    }

    @Test
    @DisplayName("Deve calcular item de serviço sem desconto e sem imposto")
    void shouldCalculateServiceItemWithoutDiscountAndTax() {
        WorkOrderCalculator.CalculatedItem item = WorkOrderCalculator.calculateItem(
                new BigDecimal("1.50"),
                new BigDecimal("100.00"),
                null,
                null,
                WorkOrderItemType.SERVICE
        );

        assertEquals(new BigDecimal("1.50"), item.quantity());
        assertEquals(new BigDecimal("100.00"), item.unitPrice());
        assertEquals(new BigDecimal("0.00"), item.discountAmount());
        assertEquals(new BigDecimal("0.00"), item.taxRate());
        assertEquals(new BigDecimal("0.00"), item.taxAmount());
        assertEquals(new BigDecimal("150.00"), item.totalAmount());
        assertEquals(WorkOrderItemType.SERVICE, item.type());
    }

    @Test
    @DisplayName("Deve calcular totais acumulando peças e serviços separadamente")
    void shouldCalculateTotalsSeparatingPartsAndServices() {
        WorkOrderCalculator.CalculatedItem part1 = WorkOrderCalculator.calculateItem(
                new BigDecimal("2.00"),
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                WorkOrderItemType.PART
        ); // Bruto: 200, Desc: 10, Total: 190

        WorkOrderCalculator.CalculatedItem part2 = WorkOrderCalculator.calculateItem(
                new BigDecimal("1.00"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                new BigDecimal("10.00"),
                WorkOrderItemType.PART
        ); // Bruto: 50, Imposto: 5, Total: 55

        WorkOrderCalculator.CalculatedItem service1 = WorkOrderCalculator.calculateItem(
                new BigDecimal("3.00"),
                new BigDecimal("80.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                WorkOrderItemType.SERVICE
        ); // Bruto: 240, Total: 240

        WorkOrderCalculator.CalculatedTotals totals = WorkOrderCalculator.calculateTotals(List.of(part1, part2, service1));

        // Subtotal bruto: 200 + 50 + 240 = 490.00
        assertEquals(new BigDecimal("490.00"), totals.subtotalAmount());
        // Desconto total: 10.00
        assertEquals(new BigDecimal("10.00"), totals.discountAmount());
        // Imposto total: 5.00
        assertEquals(new BigDecimal("5.00"), totals.taxAmount());
        // Total Peças: 190 + 55 = 245.00
        assertEquals(new BigDecimal("245.00"), totals.totalPartsAmount());
        // Total Serviços: 240.00
        assertEquals(new BigDecimal("240.00"), totals.totalServicesAmount());
        // Total Geral: 490 - 10 + 5 = 485.00 (ou 245 + 240)
        assertEquals(new BigDecimal("485.00"), totals.totalAmount());
    }

    @Test
    @DisplayName("Deve rejeitar quantidade zero ou negativa")
    void shouldRejectInvalidQuantity() {
        assertThrows(InvalidMonetaryAmountException.class, () ->
                WorkOrderCalculator.calculateItem(BigDecimal.ZERO, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, WorkOrderItemType.PART)
        );

        assertThrows(InvalidMonetaryAmountException.class, () ->
                WorkOrderCalculator.calculateItem(new BigDecimal("-1.00"), new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, WorkOrderItemType.PART)
        );
    }

    @Test
    @DisplayName("Deve rejeitar preço unitário negativo")
    void shouldRejectNegativeUnitPrice() {
        assertThrows(InvalidMonetaryAmountException.class, () ->
                WorkOrderCalculator.calculateItem(BigDecimal.ONE, new BigDecimal("-5.00"), BigDecimal.ZERO, BigDecimal.ZERO, WorkOrderItemType.SERVICE)
        );
    }

    @Test
    @DisplayName("Deve rejeitar desconto maior que o valor bruto")
    void shouldRejectDiscountGreaterThanGrossAmount() {
        assertThrows(InvalidMonetaryAmountException.class, () ->
                WorkOrderCalculator.calculateItem(BigDecimal.ONE, new BigDecimal("50.00"), new BigDecimal("55.00"), BigDecimal.ZERO, WorkOrderItemType.PART)
        );
    }

    @Test
    @DisplayName("Deve retornar totais zerados para lista de itens vazia")
    void shouldReturnZeroTotalsForEmptyList() {
        WorkOrderCalculator.CalculatedTotals totals = WorkOrderCalculator.calculateTotals(List.of());

        assertEquals(new BigDecimal("0.00"), totals.subtotalAmount());
        assertEquals(new BigDecimal("0.00"), totals.totalAmount());
        assertEquals(new BigDecimal("0.00"), totals.totalPartsAmount());
        assertEquals(new BigDecimal("0.00"), totals.totalServicesAmount());
    }
}
