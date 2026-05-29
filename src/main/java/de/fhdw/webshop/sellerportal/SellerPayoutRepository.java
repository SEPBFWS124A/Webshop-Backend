package de.fhdw.webshop.sellerportal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerPayoutRepository extends JpaRepository<SellerPayout, Long> {

    @EntityGraph(attributePaths = {"sellerProfile", "sellerProfile.user", "processedBy"})
    List<SellerPayout> findBySellerProfileIdOrderByPeriodStartDesc(Long sellerProfileId);

    @EntityGraph(attributePaths = {"sellerProfile", "sellerProfile.user", "processedBy"})
    Optional<SellerPayout> findByIdAndSellerProfileUserId(Long payoutId, Long userId);

    @EntityGraph(attributePaths = {"sellerProfile", "sellerProfile.user", "processedBy"})
    List<SellerPayout> findAllByOrderByPeriodStartDescIdDesc();

    Optional<SellerPayout> findBySellerProfileIdAndPeriodStart(Long sellerProfileId, LocalDate periodStart);
}
