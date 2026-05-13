package de.fhdw.webshop.sellerreview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SellerReviewRepository extends JpaRepository<SellerReview, Long> {

    List<SellerReview> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<SellerReview> findByOrderIdAndCustomerIdOrderByCreatedAtDesc(Long orderId, Long customerId);

    boolean existsByOrderIdAndCustomerIdAndSellerNameIgnoreCase(Long orderId, Long customerId, String sellerName);
}
