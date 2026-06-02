package de.fhdw.webshop.sellerreview;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SellerReviewRepository extends JpaRepository<SellerReview, Long> {

    List<SellerReview> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<SellerReview> findByOrderIdAndCustomerIdOrderByCreatedAtDesc(Long orderId, Long customerId);

    boolean existsByOrderIdAndCustomerIdAndSellerNameIgnoreCase(Long orderId, Long customerId, String sellerName);

    List<SellerReview> findBySellerNameIgnoreCaseOrderByCreatedAtDesc(String sellerName);

    List<SellerReview> findBySellerNameIgnoreCaseAndApprovedTrueOrderByCreatedAtDesc(String sellerName);

    Page<SellerReview> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<SellerReview> findByApprovedOrderByCreatedAtDesc(Boolean approved, Pageable pageable);

    @Query("""
            SELECT r FROM SellerReview r JOIN r.customer c
            WHERE LOWER(r.comment) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(r.sellerName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(c.username) LIKE LOWER(CONCAT('%', :search, '%'))
            ORDER BY r.createdAt DESC
            """)
    Page<SellerReview> findBySearchTermOrderByCreatedAtDesc(@Param("search") String search, Pageable pageable);

    @Query("""
            SELECT r FROM SellerReview r JOIN r.customer c
            WHERE r.approved = :approved
            AND (LOWER(r.comment) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(r.sellerName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(c.username) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY r.createdAt DESC
            """)
    Page<SellerReview> findByApprovedAndSearchTerm(@Param("approved") Boolean approved, @Param("search") String search, Pageable pageable);
}
