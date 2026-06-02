package de.fhdw.webshop.affiliate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AffiliateLinkRepository extends JpaRepository<AffiliateLink, Long> {

    Optional<AffiliateLink> findByTrackingCodeAndActiveTrue(String trackingCode);

    List<AffiliateLink> findByAffiliateProfileOrderByCreatedAtDesc(AffiliateProfile profile);

    boolean existsByAffiliateProfileAndProductId(AffiliateProfile profile, Long productId);
}
