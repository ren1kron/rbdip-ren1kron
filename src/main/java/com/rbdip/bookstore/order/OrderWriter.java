package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
class OrderWriter {

    private final OrderRepository orders;
    private final CustomerRepository customers;

    OrderWriter(OrderRepository orders, CustomerRepository customers) {
        this.orders = orders;
        this.customers = customers;
    }

    Order save(CreateOrderRequest request, List<OrderValidator.Line> lines) {
        Long customerId = customers.resolve(request.customerFullName(), request.customerAddress(), request.customerPhone());
        return orders.save(new Order(customers.getReferenceById(customerId), lines));
    }
}
