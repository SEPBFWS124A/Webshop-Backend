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

    @Query("""
            SELECT r FROM SellerReview r
            WHERE (:approvedFilter IS NULL OR r.approved = :approvedFilter)
            AND (:search IS NULL OR
                LOWER(r.comment) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(r.sellerName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(r.customer.username) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY r.createdAt DESC
            """)
    Page<SellerReview> findAllForAdmin(
            @Param("approvedFilter") Boolean approvedFilter,
            @Param("search") String search,
            Pageable pageable
    );
}
