package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class OrderValidator {

    private final ProductRepository products;

    OrderValidator(ProductRepository products) {
        this.products = products;
    }

    List<Line> validate(CreateOrderRequest request) {
        requireText(request.customerFullName(), "customerFullName");
        requireText(request.customerAddress(), "customerAddress");
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one item");
        }
        List<Long> ids = request.items().stream().map(item -> {
            if (item == null || item.productId() == null) {
                throw new IllegalArgumentException("productId is required");
            }
            return item.productId();
        }).distinct().toList();
        Map<Long, Product> catalog = products.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return request.items().stream().map(item -> resolve(item, catalog)).toList();
    }

    private static void requireText(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static Line resolve(CreateOrderRequest.Item item, Map<Long, Product> catalog) {
        Product product = catalog.get(item.productId());
        if (product == null) {
            throw new IllegalArgumentException("product " + item.productId() + " not found");
        }
        int quantity = item.quantity() == null ? 1 : item.quantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return new Line(product, quantity);
    }

    record Line(Product product, int quantity) {
        PricingCalculator.LineItem priced() {
            return new PricingCalculator.LineItem(product.getPrice(), quantity);
        }
    }
}
