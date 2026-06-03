package de.fhdw.webshop.referral;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralRepository extends JpaRepository<Referral, Long> {

    boolean existsByReferredUserId(Long userId);

    java.util.Optional<Referral> findByReferredUserId(Long userId);

    long countByReferralCodeId(Long referralCodeId);
}
