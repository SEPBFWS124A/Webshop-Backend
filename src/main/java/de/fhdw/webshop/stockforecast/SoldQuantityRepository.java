package de.fhdw.webshop.stockforecast;

import de.fhdw.webshop.order.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SoldQuantityRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
            SELECT COALESCE(SUM(oi.quantity), 0)
            FROM OrderItem oi
            JOIN oi.order o
            WHERE oi.product.id = :productId
              AND o.createdAt >= :since
            """)
    int sumSoldQuantityForProduct(@Param("productId") Long productId,
                                  @Param("since") Instant since);
}
