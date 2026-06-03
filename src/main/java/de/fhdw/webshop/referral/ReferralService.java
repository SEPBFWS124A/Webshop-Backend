package de.fhdw.webshop.referral;

import de.fhdw.webshop.discount.Coupon;
import de.fhdw.webshop.discount.CouponRepository;
import de.fhdw.webshop.notification.SystemNotificationService;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReferralService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final BigDecimal REFERRER_REWARD_EUR = new BigDecimal("5.00");
    private static final BigDecimal REFERRED_WELCOME_EUR = new BigDecimal("15.00");

    private final ReferralCodeRepository referralCodeRepository;
    private final ReferralRepository referralRepository;
    private final CouponRepository couponRepository;
    private final SystemNotificationService notificationService;

    /** Returns the caller's referral code, creating one lazily on first access. */
    @Transactional
    public ReferralCodeResponse getOrCreateCode(User referrer) {
        ReferralCode referralCode = referralCodeRepository.findByReferrerUserId(referrer.getId())
                .orElseGet(() -> createCode(referrer));
        long count = referralRepository.countByReferralCodeId(referralCode.getId());
        return new ReferralCodeResponse(referralCode.getCode(), count);
    }

    /**
     * Called by AuthService after a new user successfully registers.
     * Silently ignores invalid/already-used codes so registration is never blocked.
     */
    @Transactional
    public void processReferral(User newUser, String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        ReferralCode referralCode = referralCodeRepository.findByCode(code.trim().toUpperCase())
                .orElse(null);
        if (referralCode == null) {
            return;
        }
        // Prevent the referrer from referring themselves
        if (referralCode.getReferrerUser().getId().equals(newUser.getId())) {
            return;
        }
        // Each user can only be referred once
        if (referralRepository.existsByReferredUserId(newUser.getId())) {
            return;
        }

        // Die beworbene Person erhält ihren 15 €-Willkommensgutschein sofort bei Registrierung.
        // Die 5 €-Belohnung für den Werber wird erst bei der ersten Bestellung gewährt
        // (siehe grantReferrerRewardOnFirstOrder) — verhindert Missbrauch durch reine Account-Erstellung.
        Coupon referredCoupon = buildFixedCoupon(newUser, REFERRED_WELCOME_EUR, "WELCOME15-");
        couponRepository.save(referredCoupon);

        Referral referral = new Referral();
        referral.setReferralCode(referralCode);
        referral.setReferredUser(newUser);
        referral.setReferredCoupon(referredCoupon);
        referralRepository.save(referral);

        notificationService.createReferralWelcomeNotification(newUser, referredCoupon.getCode());
    }

    /**
     * Wird beim Aufgeben einer Bestellung der beworbenen Person aufgerufen.
     * Gewährt dem Werber einmalig den 5 €-Gutschein (idempotent über referrerRewarded-Flag).
     */
    @Transactional
    public void grantReferrerRewardOnFirstOrder(User referredUser) {
        Referral referral = referralRepository.findByReferredUserId(referredUser.getId()).orElse(null);
        if (referral == null || referral.isReferrerRewarded()) {
            return;
        }
        User referrer = referral.getReferralCode().getReferrerUser();
        Coupon referrerCoupon = buildFixedCoupon(referrer, REFERRER_REWARD_EUR, "REF5-");
        couponRepository.save(referrerCoupon);
        referral.setReferrerCoupon(referrerCoupon);
        referral.setReferrerRewarded(true);
        referralRepository.save(referral);
        notificationService.createReferralRewardNotification(referrer, referrerCoupon.getCode());
    }

    private ReferralCode createCode(User referrer) {
        String code;
        do {
            code = generateCode();
        } while (referralCodeRepository.findByCode(code).isPresent());

        ReferralCode rc = new ReferralCode();
        rc.setReferrerUser(referrer);
        rc.setCode(code);
        return referralCodeRepository.save(rc);
    }

    private Coupon buildFixedCoupon(User customer, BigDecimal amount, String prefix) {
        String code = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Coupon coupon = new Coupon();
        coupon.setCustomer(customer);
        coupon.setCode(code);
        coupon.setFixedAmountEur(amount);
        coupon.setValidUntil(LocalDate.now().plusYears(1));
        return coupon;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(SECURE_RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
