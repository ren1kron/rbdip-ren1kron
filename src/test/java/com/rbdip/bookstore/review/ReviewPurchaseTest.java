package com.rbdip.bookstore.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.rbdip.bookstore.order.CreateOrderRequest;
import com.rbdip.bookstore.purchase.PurchaseHistory;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class ReviewPurchaseTest extends AbstractIntegrationTest {

    @Autowired private PurchaseHistory purchases;

    @Test
    void checksTheBuyerAndProductWhileAcceptingReviewsFromAnyReader() {
        long purchased = product("Bought book");
        long other = product("Other book");
        var request = new CreateOrderRequest("Sam Lee", "Address", null, null, null,
                List.of(new CreateOrderRequest.Item(purchased, 1)));
        assertThat(restTemplate.postForEntity("/orders", request, JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(purchases.hasPurchased("Sam Lee", purchased)).isTrue();
        assertThat(purchases.hasPurchased("Another Sam", purchased)).isFalse();
        assertThat(purchases.hasPurchased("Sam Lee", other)).isFalse();

        var reviewed = restTemplate.postForEntity("/products/" + purchased + "/reviews",
                Map.of("authorName", "Sam Lee", "comment", "Good book", "rating", 5), JsonNode.class);
        assertThat(reviewed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reviewed.getBody().required("id").asLong()).isPositive();
        var path = "/products/" + other + "/reviews";
        assertThat(restTemplate.postForEntity(path, Map.of("rating", 4), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        JsonNode reviews = restTemplate.getForObject(path, JsonNode.class);
        assertThat(reviews.size()).isOne();
        assertThat(reviews.get(0).required("authorName").asText()).isEqualTo("anonymous");
        assertThat(reviews.get(0).required("comment").asText()).isEmpty();
        assertThat(reviews.get(0).required("rating").asInt()).isEqualTo(4);
    }

    private long product(String name) {
        return restTemplate.postForObject("/products", Map.of("name", name, "price", 10), JsonNode.class)
                .required("id").asLong();
    }
}
