package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class OrderConfirmation {

    private static final Logger LOG = LoggerFactory.getLogger(OrderConfirmation.class);

    record Placed(String customerName, Long orderId, BigDecimal total) {
    }

    @TransactionalEventListener
    public void send(Placed order) {
        LOG.info("[email] Dear {}, your order #{} for {} has been placed.",
                order.customerName(), order.orderId(), order.total());
    }
}
