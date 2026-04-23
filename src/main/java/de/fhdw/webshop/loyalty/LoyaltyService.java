package de.fhdw.webshop.loyalty;

import de.fhdw.webshop.discount.Coupon;
import de.fhdw.webshop.discount.CouponRepository;
import de.fhdw.webshop.loyalty.dto.LoyaltyStatusResponse;
import de.fhdw.webshop.loyalty.dto.SpinResultResponse;
import de.fhdw.webshop.loyalty.dto.SpinResultResponse.HighlightProduct;
import de.fhdw.webshop.loyalty.dto.WheelPrizeResponse;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class LoyaltyService {

    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final LuckyWheelPrizeRepository prizeRepository;
    private final LuckyWheelSpinRepository spinRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Value("${loyalty.streak.target-days:30}")
    private int streakTargetDays;

    @Value("${loyalty.volume.threshold-eur:500}")
    private BigDecimal volumeThreshold;

    /** Aufgerufen bei jedem Login – aktualisiert den Login-Streak. */
    @Transactional
    public void recordLogin(User user) {
        LocalDate today = LocalDate.now();
        LocalDate lastLogin = user.getLastLoginDate();

        if (lastLogin == null) {
            user.setCurrentLoginStreak(1);
        } else if (lastLogin.equals(today)) {
            return; // Bereits heute eingeloggt
        } else if (lastLogin.equals(today.minusDays(1))) {
            user.setCurrentLoginStreak(user.getCurrentLoginStreak() + 1);
        } else {
            user.setCurrentLoginStreak(1); // Streak unterbrochen
        }

        user.setLastLoginDate(today);

        if (user.getCurrentLoginStreak() >= streakTargetDays) {
            issueStreakCoupon(user);
            user.setCurrentLoginStreak(0);
        }

        userRepository.save(user);
    }

    /** Vollständigen Loyalty-Status für den eingeloggten Kunden liefern. */
    public LoyaltyStatusResponse getStatus(User user) {
        boolean canSpin = canSpinToday(user);
        BigDecimal totalSpending = calculateQualifyingSpending(user.getId());
        boolean volumeUnlocked = totalSpending.compareTo(volumeThreshold) >= 0
                && !hasActiveVolumeCoupon(user.getId());
        return new LoyaltyStatusResponse(
                user.getCurrentLoginStreak(),
                streakTargetDays,
                user.getLastLoginDate(),
                canSpin,
                volumeUnlocked,
                totalSpending,
                volumeThreshold);
    }

    /** Dreht das Glücksrad und gibt das Ergebnis zurück. */
    @Transactional
    public SpinResultResponse spin(User user) {
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Benutzer nicht gefunden."));

        if (!canSpinToday(managedUser)) {
            throw new IllegalStateException("Du hast heute bereits gedreht. Komm morgen wieder!");
        }

        List<LuckyWheelPrize> prizes = prizeRepository.findByActiveTrueOrderByProbabilityDesc();
        if (prizes.isEmpty()) {
            throw new IllegalStateException("Keine Gewinne konfiguriert.");
        }

        LuckyWheelPrize prize = selectPrize(prizes);

        LuckyWheelSpin spin = new LuckyWheelSpin();
        spin.setUser(managedUser);
        spin.setPrize(prize);

        Coupon coupon = null;
        if ("COUPON".equals(prize.getPrizeType())) {
            coupon = issueCoupon(managedUser, prize.getDiscountPercent(), "WHEEL");
            spin.setCoupon(coupon);
            spin.setWon(true);
        } else if ("FREE_SHIPPING".equals(prize.getPrizeType())) {
            spin.setWon(true);
        }

        spinRepository.save(spin);

        List<HighlightProduct> highlights = "NO_WIN".equals(prize.getPrizeType())
                ? loadHighlightProducts()
                : List.of();

        return new SpinResultResponse(
                prize.getPrizeType(),
                prize.getLabel(),
                coupon != null ? coupon.getCode() : null,
                highlights);
    }

    /** Löst den Umsatzbonus-Gutschein aus (nur einmalig). */
    @Transactional
    public String claimVolumeDiscount(User user) {
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Benutzer nicht gefunden."));
        BigDecimal totalSpending = calculateQualifyingSpending(managedUser.getId());
        if (totalSpending.compareTo(volumeThreshold) < 0) {
            throw new IllegalStateException("Schwellenwert noch nicht erreicht.");
        }
        if (hasActiveVolumeCoupon(managedUser.getId())) {
            throw new IllegalStateException("Umsatz-Rabatt wurde bereits ausgestellt.");
        }
        Coupon coupon = issueCoupon(managedUser, new BigDecimal("5.00"), "VOL");
        return coupon.getCode();
    }

    /** Alle konfigurierten Gewinntöpfe abrufen (für Admin-Ansicht). */
    public List<WheelPrizeResponse> listPrizes() {
        return prizeRepository.findAll().stream()
                .map(p -> new WheelPrizeResponse(
                        p.getId(), p.getLabel(), p.getPrizeType(),
                        p.getDiscountPercent(), p.getProbability(), p.isActive()))
                .toList();
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private boolean canSpinToday(User user) {
        return spinRepository.findFirstByUserIdOrderBySpunAtDesc(user.getId())
                .map(s -> s.getSpunAt().isBefore(Instant.now().minus(24, ChronoUnit.HOURS)))
                .orElse(true);
    }

    private LuckyWheelPrize selectPrize(List<LuckyWheelPrize> prizes) {
        double random = new Random().nextDouble();
        double cumulative = 0.0;
        for (LuckyWheelPrize p : prizes) {
            cumulative += p.getProbability().doubleValue();
            if (random < cumulative) {
                return p;
            }
        }
        return prizes.get(prizes.size() - 1);
    }

    private Coupon issueCoupon(User user, BigDecimal discountPercent, String prefix) {
        Coupon coupon = new Coupon();
        coupon.setCustomer(user);
        coupon.setCode(prefix + "-" + user.getId() + "-" + System.currentTimeMillis());
        coupon.setDiscountPercent(discountPercent);
        coupon.setValidUntil(LocalDate.now().plusDays(90));
        return couponRepository.save(coupon);
    }

    private void issueStreakCoupon(User user) {
        issueCoupon(user, new BigDecimal("5.00"), "STREAK30");
    }

    private BigDecimal calculateQualifyingSpending(Long userId) {
        // Nur abgelieferte Bestellungen, Rückgabefrist (14 Tage) abgelaufen
        Instant cutoff = Instant.now().minus(14, ChronoUnit.DAYS);
        BigDecimal result = orderRepository.sumDeliveredSpendingBefore(userId, OrderStatus.DELIVERED.name(), cutoff);
        return result != null ? result : BigDecimal.ZERO;
    }

    private boolean hasActiveVolumeCoupon(Long userId) {
        return couponRepository.findByCustomerId(userId).stream()
                .anyMatch(c -> !c.isUsed() && c.getCode().startsWith("VOL-" + userId + "-"));
    }

    private List<HighlightProduct> loadHighlightProducts() {
        return productRepository.searchProducts(true, "", "")
                .stream()
                .filter(Product::isPromoted)
                .limit(4)
                .map(p -> new HighlightProduct(p.getId(), p.getName(), p.getCategory(), p.getRecommendedRetailPrice()))
                .toList();
    }
}
