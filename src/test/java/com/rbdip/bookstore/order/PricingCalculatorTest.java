package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest
    @CsvSource(value = {
        "100, 10, regular, NULL, 1000.00",
        "100, 11, regular, NULL, 1024.10",
        "999.99, 1, NULL, NULL, 999.99",
        "1000.01, 1, regular, NULL, 980.01",
        "100, 1, vip, SAVE10, 80.00",
        "100, 1, wholesale, SAVE20PERCENT, 68.00",
        "1010, 1, regular, SAVE10, 1000.00",
        "100, 1, VIP, save10, 100.00",
        "4, 1, regular, SAVE10, 0.00",
        "7, -2, regular, NULL, 0.00",
        "-12.50, 1, NULL, NULL, 0.00",
        "10, 0, regular, NULL, 0.00",
        "1.005, 1, regular, NULL, 1.01",
        "1.004, 1, regular, NULL, 1.00"
    }, nullValues = "NULL")
    void appliesDiscountsAndRoundsTheFinalTotal(
            BigDecimal price, int quantity, String type, String coupon, BigDecimal expected) {
        var items = List.of(new PricingCalculator.LineItem(price, quantity));
        assertThat(calculator.calculateOrderTotal(items, type, coupon)).isEqualTo(expected);
    }

    @Test
    void appliesBulkDiscountPerLineBeforeTheCustomerDiscount() {
        var items = List.of(new PricingCalculator.LineItem(new BigDecimal("10"), 11),
                new PricingCalculator.LineItem(new BigDecimal("10"), 2));
        assertThat(calculator.calculateOrderTotal(items, "vip", null)).isEqualTo(new BigDecimal("112.05"));
    }

    @Test
    void anEmptyBasketCostsZeroEvenWithAFixedCoupon() {
        assertThat(calculator.calculateOrderTotal(List.of(), null, "SAVE10")).isEqualTo(new BigDecimal("0.00"));
    }
}
