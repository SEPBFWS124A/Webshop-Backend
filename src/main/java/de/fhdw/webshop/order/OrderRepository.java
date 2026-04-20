package de.fhdw.webshop.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    long countByCustomerId(Long customerId);

    Optional<Order> findFirstByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<Order> findByIdAndCustomerId(Long orderId, Long customerId);

    /** US #29 — Orders for a customer within a date range (for revenue statistics). */
    @Query("SELECT o FROM Order o WHERE o.customer.id = :customerId AND o.createdAt BETWEEN :from AND :to")
    List<Order> findByCustomerIdAndCreatedAtBetween(
            @Param("customerId") Long customerId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /** US #34, #36 — Order items for a product within a date range (for sales statistics). */
    @Query("""
            SELECT oi FROM OrderItem oi
            WHERE oi.product.id = :productId
              AND oi.order.createdAt BETWEEN :from AND :to
            """)
    List<OrderItem> findOrderItemsByProductIdAndDateRange(
            @Param("productId") Long productId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
