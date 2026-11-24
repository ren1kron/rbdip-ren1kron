package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(OutputCaptureExtension.class)
class OrderPlacementTest extends AbstractIntegrationTest {

    @Autowired private OrderService orders;
    @Autowired private ProductRepository products;
    @Autowired private TransactionTemplate transactions;

    @Test
    void sendsThePricedConfirmationOnlyForCommittedOrders(CapturedOutput output) {
        Long productId = productId();
        var request = request("Confirmation Buyer", "Address", new CreateOrderRequest.Item(productId, null));
        transactions.executeWithoutResult(status -> {
            orders.createOrder(request);
            status.setRollbackOnly();
        });
        assertThat(output).doesNotContain("[email] Dear Confirmation Buyer");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
        var saved = orders.createOrder(request);
        assertThat(output).contains("[email] Dear Confirmation Buyer, your order #" + saved.getId() + " for 8.50");
        assertThat(jdbcTemplate.queryForObject("SELECT quantity FROM order_items", Integer.class)).isOne();
    }

    @Test
    void reportsValidationErrorsWithoutPersistingAPartialOrder() {
        var valid = new CreateOrderRequest.Item(productId(), 2);
        assertInvalid(request(" ", "Address", valid), "customerFullName is required");
        assertInvalid(request("Buyer", null, valid), "customerAddress is required");
        assertInvalid(request("Buyer", "Address"), "order must contain at least one item");
        assertInvalid(request("Buyer", "Address", valid, new CreateOrderRequest.Item(valid.productId(), 0)),
                "quantity must be positive");
        assertInvalid(request("Buyer", "Address", valid, new CreateOrderRequest.Item(-1L, 2)),
                "product -1 not found");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
    }

    private void assertInvalid(CreateOrderRequest request, String message) {
        var response = restTemplate.postForEntity("/orders", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", message);
    }

    private Long productId() {
        return products.save(new Product("One book", new BigDecimal("8.50"), null)).getId();
    }

    private static CreateOrderRequest request(String name, String address, CreateOrderRequest.Item... items) {
        return new CreateOrderRequest(name, address, null, null, null, Arrays.asList(items));
    }
}
