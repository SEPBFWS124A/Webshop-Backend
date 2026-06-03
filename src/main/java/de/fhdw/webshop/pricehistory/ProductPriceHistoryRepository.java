package de.fhdw.webshop.pricehistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProductPriceHistoryRepository extends JpaRepository<ProductPriceHistory, Long> {

    List<ProductPriceHistory> findByProductIdAndChangedAtBetweenOrderByChangedAtAsc(
            Long productId, Instant from, Instant to);

    List<ProductPriceHistory> findByProductIdOrderByChangedAtAsc(Long productId);

    Optional<ProductPriceHistory> findTopByProductIdOrderByChangedAtDesc(Long productId);
}
