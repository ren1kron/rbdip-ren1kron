package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderValidator validator;
    private final PricingCalculator pricing;
    private final OrderWriter writer;
    private final ApplicationEventPublisher events;

    public OrderService(OrderValidator validator, PricingCalculator pricing, OrderWriter writer,
            ApplicationEventPublisher events) {
        this.validator = validator;
        this.pricing = pricing;
        this.writer = writer;
        this.events = events;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        List<OrderValidator.Line> lines = validator.validate(request);
        BigDecimal total = pricing.calculateOrderTotal(lines.stream().map(OrderValidator.Line::priced).toList(),
                request.customerType(), request.couponCode());
        Order order = writer.save(request, lines);
        events.publishEvent(new OrderConfirmation.Placed(request.customerFullName(), order.getId(), total));
        return order;
    }
}
