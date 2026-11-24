package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class OrderListingTest extends AbstractIntegrationTest {

    @Autowired private EntityManagerFactory entityManager;

    @Test
    void oneSelectListsBothRepeatedLinesAndHistoricalOrdersWithoutLines() {
        var product = restTemplate.postForObject("/products", Map.of("name", "Patterns", "price", 9), JsonNode.class);
        long productId = product.required("id").asLong();
        var request = new CreateOrderRequest(" Q  Lyn ", "Street", null, null, null, List.of(
                new CreateOrderRequest.Item(productId, 1), new CreateOrderRequest.Item(productId, 3)));
        long first = restTemplate.postForObject("/orders", request, JsonNode.class).required("id").asLong();
        long empty = jdbcTemplate.queryForObject("""
                INSERT INTO orders(customer_id) SELECT customer_id FROM orders WHERE id = ? RETURNING id
                """, Long.class, first);
        var statistics = entityManager.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var response = restTemplate.getForEntity("/orders", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statistics.getPrepareStatementCount()).isOne();
        JsonNode orders = response.getBody();
        assertThat(orders.size()).isEqualTo(2);
        assertThat(orders.get(0).required("id").asLong()).isEqualTo(first);
        assertThat(orders.get(0).required("customerFullName").asText()).isEqualTo(" Q  Lyn ");
        var items = orders.get(0).required("items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).required("quantity").asInt()).isOne();
        assertThat(items.get(1).required("quantity").asInt()).isEqualTo(3);
        assertThat(items.get(1).required("productName").asText()).isEqualTo("Patterns");
        assertThat(orders.get(1).required("id").asLong()).isEqualTo(empty);
        assertThat(orders.get(1).required("items").size()).isZero();
    }
}
