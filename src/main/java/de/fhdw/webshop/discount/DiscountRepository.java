package de.fhdw.webshop.discount;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    List<Discount> findByCustomerId(Long customerId);

    Optional<Discount> findByCustomerIdAndProductId(Long customerId, Long productId);

    /** Returns active discounts for a customer on a specific product (for price calculation). */
    @Query("""
            SELECT d FROM Discount d
            WHERE d.customer.id = :customerId
              AND d.product.id = :productId
              AND d.validFrom <= :today
              AND (d.validUntil IS NULL OR d.validUntil >= :today)
            ORDER BY d.discountPercent DESC
            """)
    List<Discount> findActiveDiscounts(
            @Param("customerId") Long customerId,
            @Param("productId") Long productId,
            @Param("today") LocalDate today
    );
}
