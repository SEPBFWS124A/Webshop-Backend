package de.fhdw.webshop.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductFeedbackRepository extends JpaRepository<ProductFeedback, Long> {

    List<ProductFeedback> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<ProductFeedback> findByProductIdAndApprovedTrueOrderByCreatedAtDesc(Long productId);

    Page<ProductFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ProductFeedback> findByApprovedOrderByCreatedAtDesc(Boolean approved, Pageable pageable);

    @Query("SELECT f FROM ProductFeedback f WHERE LOWER(f.comment) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY f.createdAt DESC")
    Page<ProductFeedback> findBySearchTermOrderByCreatedAtDesc(@Param("search") String search, Pageable pageable);

    @Query("SELECT f FROM ProductFeedback f WHERE f.approved = :approved AND LOWER(f.comment) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY f.createdAt DESC")
    Page<ProductFeedback> findByApprovedAndSearchTerm(@Param("approved") Boolean approved, @Param("search") String search, Pageable pageable);

    boolean existsByProductIdAndUserId(Long productId, Long userId);

    long countByProductId(Long productId);

    long countByProductIdAndApprovedTrue(Long productId);

    @Query("SELECT AVG(f.rating) FROM ProductFeedback f WHERE f.productId = :productId")
    Double findAverageRatingByProductId(@Param("productId") Long productId);

    @Query("SELECT AVG(f.rating) FROM ProductFeedback f WHERE f.productId = :productId AND f.approved = TRUE")
    Double findAverageRatingByProductIdApproved(@Param("productId") Long productId);

    @Query("""
            SELECT AVG(f.rating) FROM ProductFeedback f
            WHERE f.productId IN (
                SELECT p.id FROM Product p WHERE LOWER(p.sellerName) = LOWER(:sellerName)
            )
            AND f.approved = TRUE
            """)
    Double findAverageRatingBySellerName(@Param("sellerName") String sellerName);
}
