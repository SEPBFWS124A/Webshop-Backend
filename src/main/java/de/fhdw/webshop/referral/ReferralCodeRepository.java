package de.fhdw.webshop.referral;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReferralCodeRepository extends JpaRepository<ReferralCode, Long> {

    Optional<ReferralCode> findByReferrerUserId(Long userId);

    Optional<ReferralCode> findByCode(String code);
}
