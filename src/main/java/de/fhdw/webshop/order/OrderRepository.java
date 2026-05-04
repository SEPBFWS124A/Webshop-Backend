package de.fhdw.webshop.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

        List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

        long countByCustomerId(Long customerId);

        Optional<Order> findFirstByCustomerIdOrderByCreatedAtDesc(Long customerId);

        Optional<Order> findByIdAndCustomerId(Long orderId, Long customerId);

        List<Order> findByStatusOrderByCreatedAtAsc(OrderStatus status);

        List<Order> findByStatusNotInOrderByCreatedAtAsc(Collection<OrderStatus> statuses);

        @Query("""
                        SELECT DISTINCT o FROM Order o
                        JOIN FETCH o.customer customer
                        LEFT JOIN FETCH o.items item
                        LEFT JOIN FETCH item.product
                        WHERE o.status = :status
                          AND customer.id <> :managerId
                          AND EXISTS (
                                SELECT link.id FROM AccountLink link
                                WHERE (link.userA.id = :managerId AND link.userB.id = customer.id)
                                   OR (link.userB.id = :managerId AND link.userA.id = customer.id)
                          )
                        ORDER BY o.createdAt ASC
                        """)
        List<Order> findApprovalRequestsForManager(
                        @Param("managerId") Long managerId,
                        @Param("status") OrderStatus status);

        @Query("""
                        SELECT DISTINCT o FROM Order o
                        JOIN FETCH o.customer customer
                        LEFT JOIN FETCH o.items item
                        LEFT JOIN FETCH item.product
                        WHERE o.id = :orderId
                          AND customer.id <> :managerId
                          AND EXISTS (
                                SELECT link.id FROM AccountLink link
                                WHERE (link.userA.id = :managerId AND link.userB.id = customer.id)
                                   OR (link.userB.id = :managerId AND link.userA.id = customer.id)
                          )
                        """)
        Optional<Order> findApprovalRequestForManager(
                        @Param("orderId") Long orderId,
                        @Param("managerId") Long managerId);

        /**
         * US #29 — Orders for a customer within a date range (for revenue statistics).
         */
        @Query("SELECT o FROM Order o WHERE o.customer.id = :customerId AND o.createdAt BETWEEN :from AND :to")
        List<Order> findByCustomerIdAndCreatedAtBetween(
                        @Param("customerId") Long customerId,
                        @Param("from") Instant from,
                        @Param("to") Instant to);

        /**
         * US #34, #36 — Order items for a product within a date range (for sales
         * statistics).
         */
        @Query("""
                        SELECT oi FROM OrderItem oi
                        WHERE oi.product.id = :productId
                          AND oi.order.createdAt BETWEEN :from AND :to
                        """)
        List<OrderItem> findOrderItemsByProductIdAndDateRange(
                        @Param("productId") Long productId,
                        @Param("from") Instant from,
                        @Param("to") Instant to);

        /**
         * US-2 (Issue #116) — Umsatz aus DELIVERED-Bestellungen vor dem Cutoff-Datum
         * (nach Rückgabefrist).
         */
        @Query(value = """
                        SELECT SUM(o.total_price)
                        FROM orders o
                        WHERE o.customer_id = :customerId
                          AND o.status = CAST(:status AS order_status)
                          AND o.created_at < :cutoff
                        """, nativeQuery = true)
        BigDecimal sumDeliveredSpendingBefore(
                        @Param("customerId") Long customerId,
                        @Param("status") String status,
                        @Param("cutoff") Instant cutoff);
}
