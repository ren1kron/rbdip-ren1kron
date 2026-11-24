package com.rbdip.bookstore.order;

import com.rbdip.bookstore.purchase.PurchaseHistory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface OrderItemRepository extends Repository<OrderItem, Long>, PurchaseHistory {

    @Override
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM order_items i
                JOIN orders o ON o.id = i.order_id
                JOIN customers c ON c.id = o.customer_id
                WHERE i.product_id = :productId AND customer_display_name(c.first_name, c.last_name) = :customerName
            )
            """, nativeQuery = true)
    boolean hasPurchased(@Param("customerName") String customerName, @Param("productId") Long productId);
}
