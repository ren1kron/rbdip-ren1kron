package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MigrationPathTest {

    @Container
    final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;

    @BeforeEach
    void connect() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    @Test
    void normalizesPopulatedOrdersAndRecoversHistoricalProducts() {
        migrate("1");
        jdbc.update("""
                INSERT INTO products(name, price) VALUES ('Book', 10), ('Book', 10), ('Book', 12)
                """);
        jdbc.update("""
                INSERT INTO orders(customer_full_name, customer_address, customer_phone)
                VALUES ('Rae Lee', 'Road', NULL), ('Rae Lee', 'Road', NULL),
                       ('Rae Lee', 'Road', ''), ('Solo', NULL, NULL)
                """);
        jdbc.update("""
                INSERT INTO order_items(order_id, product_name, product_price, quantity)
                VALUES (1, 'Book', 10, 2), (2, 'Book', 12, 3),
                       (3, 'Book', 9, 1), (4, 'Book', 9, 5)
                """);
        migrate("2");

        assertThat(jdbc.queryForList("""
                SELECT c.full_name FROM orders o JOIN customers c ON c.id = o.customer_id ORDER BY o.id
                """, String.class)).containsExactly("Rae Lee", "Rae Lee", "Rae Lee", "Solo");
        var customerIds = jdbc.queryForList("SELECT customer_id FROM orders ORDER BY id", Long.class);
        assertThat(customerIds).hasSize(4);
        assertThat(customerIds.get(0)).isEqualTo(customerIds.get(1)).isNotEqualTo(customerIds.get(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT product_id FROM order_items ORDER BY id", Long.class))
                .containsExactly(1L, 3L, 4L, 4L);
        assertThat(jdbc.queryForList("SELECT quantity FROM order_items ORDER BY id", Integer.class))
                .containsExactly(2, 3, 1, 5);
        assertThat(jdbc.queryForObject("SELECT price FROM products WHERE id = 4", Integer.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(4);
    }

    @Test
    void backfillsExistingNamesAndAcceptsBothWritersDuringTheRollout() throws Exception {
        migrate("2");
        jdbc.update("INSERT INTO customers(full_name) VALUES (?), (?), (?)", "  Rae  van Lee  ", "Solo ", "   ");
        migrate("3");
        // This old writer stays in flight until after the backfill commits.
        try (Connection legacy = jdbc.getDataSource().getConnection(); Statement sql = legacy.createStatement()) {
            legacy.setAutoCommit(false);
            sql.executeUpdate("INSERT INTO customers(full_name) VALUES ('Иванов Иван')");
            migrate("4");
            legacy.commit();
        }
        assertParts("  Rae  van Lee  ", "  Rae", " van Lee  ");
        assertParts("Solo ", "Solo ", "");
        assertParts("   ", "   ", "");
        assertParts("Иванов Иван", "Иванов", "Иван");

        jdbc.update("INSERT INTO customers(first_name, last_name) VALUES ('New', 'Writer')");
        assertParts("New Writer", "New", "Writer");
        jdbc.update("UPDATE customers SET full_name = 'Old Writer Jr' WHERE full_name = 'New Writer'");
        assertParts("Old Writer Jr", "Old", "Writer Jr");
        jdbc.update("UPDATE customers SET first_name = 'Renamed' WHERE full_name = 'Old Writer Jr'");
        assertParts("Renamed Writer Jr", "Renamed", "Writer Jr");
    }

    private void assertParts(String fullName, String firstName, String lastName) {
        var result = jdbc.queryForMap("SELECT first_name, last_name FROM customers WHERE full_name = ?", fullName);
        assertThat(result).containsEntry("first_name", firstName).containsEntry("last_name", lastName);
    }

    @Test
    void usesTheOriginalColumnLengthsWhenIdentifyingCustomers() {
        migrate("2");
        checkContactLimits();
    }

    private void checkContactLimits() {
        String name = "A".repeat(250) + " B" + " ".repeat(10);
        String address = "門".repeat(500) + "  ";
        String phone = "1".repeat(50) + "  ";
        String sql = "SELECT resolve_customer(?, ?, ?)";
        Long original = jdbc.queryForObject(sql, Long.class, name, address, phone);
        assertThat(jdbc.queryForObject(sql, Long.class, name.substring(0, 255),
                address.substring(0, 500), phone.substring(0, 50))).isEqualTo(original);
        assertThat(jdbc.queryForObject(sql, Long.class, name, address, phone)).isEqualTo(original);
        assertThatThrownBy(() -> jdbc.queryForObject(sql, Long.class, "X".repeat(256), "Road", null))
                .rootCause().isInstanceOfSatisfying(SQLException.class,
                        e -> assertThat(e.getSQLState()).isEqualTo("22001"));
    }

    @Test
    void appliesLegacyColumnLimitsBeforeSplittingNames() {
        migrate("4");
        checkContactLimits();
    }

    @Test
    void aBusyExpandCanRetryWithoutLosingLegacyWrites() throws Exception {
        migrate("2");
        try (Connection busy = jdbc.getDataSource().getConnection(); Statement sql = busy.createStatement()) {
            busy.setAutoCommit(false);
            sql.executeUpdate("INSERT INTO customers(full_name) VALUES ('Active Reader')");
            assertThatThrownBy(() -> Flyway.configure().dataSource(jdbc.getDataSource()).target("3")
                    .initSql("SET statement_timeout = '6s'").load().migrate())
                    .rootCause().isInstanceOfSatisfying(SQLException.class,
                            e -> assertThat(e.getSQLState()).isEqualTo("55P03"));
            busy.commit();
        }
        migrate("4");
        assertParts("Active Reader", "Active", "Reader");
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(jdbc.getDataSource()).target(target).load().migrate();
    }
}
