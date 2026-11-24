package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.rbdip.bookstore.order.CreateOrderRequest;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.flyway.target=4")
class OrderApiMigrationLoadTest {

    private static final int REQUESTS_PER_WORKER = 6;
    private static final String BUYER = "  Ada  van Rossum  ";
    private static final String ORIGINAL_BUYER = "Original Buyer";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private TestRestTemplate http;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @Timeout(90)
    void orderRequestsInFlightDuringContractFinishWithIntactData() throws Exception {
        var transport = new SimpleClientHttpRequestFactory();
        transport.setConnectTimeout(Duration.ofSeconds(5));
        transport.setReadTimeout(Duration.ofSeconds(20));
        http.getRestTemplate().setRequestFactory(transport);
        var product = http.postForEntity("/products", Map.of("name", "Migration book", "price", "12.50"), JsonNode.class);
        assertThat(product.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long productId = product.getBody().required("id").asLong();
        List<Long> expected = new ArrayList<>();
        expected.add(placeOrder(ORIGINAL_BUYER, productId));
        assertThat(listOrders()).containsExactlyElementsOf(expected);

        expected.addAll(runTrafficDuringContract(productId, expected.getFirst()));
        expected.add(placeOrder(BUYER, productId));
        assertThat(listOrders()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'customers'
                """, String.class)).contains("first_name", "last_name").doesNotContain("full_name");
    }

    private List<Long> runTrafficDuringContract(long productId, long originalOrder) throws Exception {
        try (var workers = Executors.newFixedThreadPool(5)) {
            Future<?> contract;
            Future<List<Long>> firstWriter;
            Future<List<Long>> secondWriter;
            Future<?> firstReader;
            Future<?> secondReader;
            // Queue the real DDL and HTTP queries on a known lock instead of guessing with sleeps.
            try (Connection gate = dataSource.getConnection(); Statement lock = gate.createStatement()) {
                gate.setAutoCommit(false);
                lock.execute("LOCK TABLE customers IN ACCESS SHARE MODE");
                contract = workers.submit(() -> Flyway.configure().dataSource(dataSource).target("5").load().migrate());
                awaitContractLock(contract);
                firstWriter = workers.submit(() -> writeOrders(productId));
                firstReader = workers.submit(() -> readOrders(originalOrder));
                // Exactly one GET and one POST are active at this point.
                await().atMost(Duration.ofSeconds(6)).until(() -> waiters("AccessShareLock") >= 2);
                secondWriter = workers.submit(() -> writeOrders(productId));
                secondReader = workers.submit(() -> readOrders(originalOrder));
                gate.commit();
            }
            contract.get(20, TimeUnit.SECONDS);
            firstReader.get(20, TimeUnit.SECONDS);
            secondReader.get(20, TimeUnit.SECONDS);
            List<Long> ids = new ArrayList<>(firstWriter.get(20, TimeUnit.SECONDS));
            ids.addAll(secondWriter.get(20, TimeUnit.SECONDS));
            return ids;
        }
    }

    private void awaitContractLock(Future<?> contract) {
        await().atMost(Duration.ofSeconds(8)).until(() -> {
            if (contract.isDone()) {
                contract.get();
            }
            return waiters("AccessExclusiveLock") > 0;
        });
    }

    private List<Long> writeOrders(long productId) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < REQUESTS_PER_WORKER; i++) {
            ids.add(placeOrder(BUYER, productId));
        }
        return ids;
    }

    private void readOrders(long originalOrder) {
        for (int i = 0; i < REQUESTS_PER_WORKER; i++) {
            assertThat(listOrders()).contains(originalOrder);
        }
    }

    private int waiters(String mode) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM pg_locks WHERE relation = 'customers'::regclass AND NOT granted AND mode = ?
                """, Integer.class, mode);
    }

    private long placeOrder(String buyer, long productId) {
        var request = new CreateOrderRequest(buyer, "Test street", null, null, null,
                List.of(new CreateOrderRequest.Item(productId, 2)));
        var response = http.postForEntity("/orders", request, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().required("status").asText()).isEqualTo("new");
        return response.getBody().required("id").asLong();
    }

    private List<Long> listOrders() {
        var response = http.getForEntity("/orders", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Long> ids = new ArrayList<>();
        for (JsonNode order : response.getBody()) {
            ids.add(order.required("id").asLong());
            assertThat(order.required("status").asText()).isEqualTo("new");
            assertThat(order.required("customerFullName").asText()).isIn(ORIGINAL_BUYER, BUYER);
            JsonNode items = order.required("items");
            assertThat(items.size()).isOne();
            assertThat(items.get(0).required("productName").asText()).isEqualTo("Migration book");
            assertThat(items.get(0).required("quantity").asInt()).isEqualTo(2);
        }
        return ids;
    }
}
