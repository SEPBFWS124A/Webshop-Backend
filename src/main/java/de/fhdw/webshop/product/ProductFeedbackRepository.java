package de.fhdw.webshop.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductFeedbackRepository extends JpaRepository<ProductFeedback, Long> {

    List<ProductFeedback> findByProductIdOrderByCreatedAtDesc(Long productId);

    boolean existsByProductIdAndUserId(Long productId, Long userId);

    long countByProductId(Long productId);

    @Query("SELECT AVG(f.rating) FROM ProductFeedback f WHERE f.productId = :productId")
    Double findAverageRatingByProductId(@Param("productId") Long productId);

    @Query("""
            SELECT AVG(f.rating) FROM ProductFeedback f
            WHERE f.productId IN (
                SELECT p.id FROM Product p WHERE LOWER(p.sellerName) = LOWER(:sellerName)
            )
            """)
    Double findAverageRatingBySellerName(@Param("sellerName") String sellerName);
}
