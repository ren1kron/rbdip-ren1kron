package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
class OrderWriter {

    private final OrderRepository orders;
    private final OrderItemRepository items;

    OrderWriter(OrderRepository orders, OrderItemRepository items) {
        this.orders = orders;
        this.items = items;
    }

    Order save(CreateOrderRequest request, List<OrderValidator.Line> lines) {
        Order order = orders.save(new Order(
                request.customerFullName(), request.customerAddress(), request.customerPhone(), "new"));
        for (OrderValidator.Line line : lines) {
            items.save(new OrderItem(order.getId(), line.product().getName(), line.product().getPrice(), line.quantity()));
        }
        return order;
    }
}
