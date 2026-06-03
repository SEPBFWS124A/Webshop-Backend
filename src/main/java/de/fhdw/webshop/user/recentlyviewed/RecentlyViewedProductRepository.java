package de.fhdw.webshop.user.recentlyviewed;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecentlyViewedProductRepository extends JpaRepository<RecentlyViewedProduct, Long> {

    Optional<RecentlyViewedProduct> findByUserIdAndProductId(Long userId, Long productId);

    @EntityGraph(attributePaths = {"product"})
    List<RecentlyViewedProduct> findByUserIdOrderByViewedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"product"})
    @Query("SELECT r FROM RecentlyViewedProduct r WHERE r.user.id = :userId AND LOWER(r.product.sellerName) != 'webshop' ORDER BY r.viewedAt DESC")
    List<RecentlyViewedProduct> findMarketplaceByUserIdOrderByViewedAtDesc(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"product"})
    @Query("SELECT r FROM RecentlyViewedProduct r WHERE r.user.id = :userId AND LOWER(r.product.sellerName) = 'webshop' ORDER BY r.viewedAt DESC")
    List<RecentlyViewedProduct> findShopByUserIdOrderByViewedAtDesc(@Param("userId") Long userId);
}
