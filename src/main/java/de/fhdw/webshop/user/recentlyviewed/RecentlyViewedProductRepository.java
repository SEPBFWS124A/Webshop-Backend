package de.fhdw.webshop.user.recentlyviewed;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecentlyViewedProductRepository extends JpaRepository<RecentlyViewedProduct, Long> {

    Optional<RecentlyViewedProduct> findByUserIdAndProductId(Long userId, Long productId);

    @EntityGraph(attributePaths = {"product"})
    List<RecentlyViewedProduct> findByUserIdOrderByViewedAtDesc(Long userId);
}
