package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PricingCalculator {

    private static final int BULK_QUANTITY = 10;
    private static final BigDecimal BULK_MULTIPLIER = new BigDecimal("0.95");
    private static final BigDecimal VIP_MULTIPLIER = new BigDecimal("0.90");
    private static final BigDecimal WHOLESALE_MULTIPLIER = new BigDecimal("0.85");
    private static final BigDecimal COUPON_MULTIPLIER = new BigDecimal("0.80");
    private static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal LARGE_ORDER_MULTIPLIER = new BigDecimal("0.98");

    public record LineItem(BigDecimal price, int quantity) {
    }

    public BigDecimal calculateOrderTotal(List<LineItem> items, String customerType, String couponCode) {
        BigDecimal subtotal = items.stream().map(this::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discounted = subtotal.multiply(customerMultiplier(customerType));
        BigDecimal total = applyCoupon(discounted, couponCode).max(BigDecimal.ZERO);
        if (total.compareTo(LARGE_ORDER_THRESHOLD) > 0) {
            total = total.multiply(LARGE_ORDER_MULTIPLIER);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal lineTotal(LineItem item) {
        BigDecimal total = item.price().multiply(BigDecimal.valueOf(item.quantity()));
        return item.quantity() > BULK_QUANTITY ? total.multiply(BULK_MULTIPLIER) : total;
    }

    private BigDecimal customerMultiplier(String customerType) {
        return switch (customerType) {
            case "vip" -> VIP_MULTIPLIER;
            case "wholesale" -> WHOLESALE_MULTIPLIER;
            case null, default -> BigDecimal.ONE;
        };
    }

    private BigDecimal applyCoupon(BigDecimal total, String couponCode) {
        return switch (couponCode) {
            case "SAVE10" -> total.subtract(BigDecimal.TEN);
            case "SAVE20PERCENT" -> total.multiply(COUPON_MULTIPLIER);
            case null, default -> total;
        };
    }
}
