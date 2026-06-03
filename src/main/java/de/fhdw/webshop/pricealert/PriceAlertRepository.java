package de.fhdw.webshop.pricealert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    List<PriceAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PriceAlert> findByStatusAndActiveTrue(PriceAlertStatus status);

    List<PriceAlert> findByProductIdAndStatusAndActiveTrue(Long productId, PriceAlertStatus status);

    @Query("SELECT CASE WHEN COUNT(pa) > 0 THEN true ELSE false END FROM PriceAlert pa " +
            "WHERE pa.user.id = :userId AND pa.product.id = :productId " +
            "AND pa.targetPrice = :targetPrice AND pa.active = true AND pa.status = 'ACTIVE'")
    boolean existsDuplicate(@Param("userId") Long userId,
                            @Param("productId") Long productId,
                            @Param("targetPrice") BigDecimal targetPrice);
}

