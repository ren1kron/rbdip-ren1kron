package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderListing {

    private final OrderRepository orders;

    OrderListing(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public List<Summary> all() {
        return orders.findAllByOrderByIdAsc().stream().map(Summary::from).toList();
    }

    record Summary(Long id, String customerFullName, String status, List<Item> items) {
        static Summary from(Order order) {
            return new Summary(order.getId(), order.getCustomerFullName(), order.getStatus(),
                    order.items().stream().map(line -> new Item(line.getProductName(), line.getQuantity())).toList());
        }
    }

    record Item(String productName, int quantity) {
    }
}
