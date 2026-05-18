package de.fhdw.webshop.sellerreview;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerReviewImageRepository extends JpaRepository<SellerReviewImage, Long> {

    List<SellerReviewImage> findByReviewIdOrderByCreatedAtAsc(Long reviewId);

    List<SellerReviewImage> findAllByOrderByCreatedAtDesc();

    long countByReviewId(Long reviewId);
}
