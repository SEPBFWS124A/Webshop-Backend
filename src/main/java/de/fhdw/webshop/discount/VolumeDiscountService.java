package de.fhdw.webshop.discount;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.discount.dto.VolumeDiscountTierRequest;
import de.fhdw.webshop.discount.dto.VolumeDiscountTierResponse;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VolumeDiscountService {

    public static final String DISCOUNT_TYPE = "VOLUME";

    private final VolumeDiscountTierRepository tierRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<VolumeDiscountTierResponse> listAll() {
        return tierRepository.findAllByOrderByActiveDescDiscountPercentDescIdDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public VolumeDiscountTierResponse create(VolumeDiscountTierRequest request, User actingUser) {
        VolumeDiscountTier tier = new VolumeDiscountTier();
        applyRequest(tier, request);
        VolumeDiscountTier savedTier = tierRepository.save(tier);
        recordAction(actingUser, "CREATE_VOLUME_DISCOUNT_TIER", savedTier,
                "Volume discount tier created: " + savedTier.getName());
        return toResponse(savedTier);
    }

    @Transactional
    public VolumeDiscountTierResponse update(Long tierId, VolumeDiscountTierRequest request, User actingUser) {
        VolumeDiscountTier tier = loadTier(tierId);
        applyRequest(tier, request);
        VolumeDiscountTier savedTier = tierRepository.save(tier);
        recordAction(actingUser, "UPDATE_VOLUME_DISCOUNT_TIER", savedTier,
                "Volume discount tier updated: " + savedTier.getName());
        return toResponse(savedTier);
    }

    @Transactional
    public VolumeDiscountTierResponse setActive(Long tierId, boolean active, User actingUser) {
        VolumeDiscountTier tier = loadTier(tierId);
        tier.setActive(active);
        VolumeDiscountTier savedTier = tierRepository.save(tier);
        recordAction(actingUser, "SET_VOLUME_DISCOUNT_TIER_ACTIVE", savedTier,
                "Volume discount tier active set to " + active + ": " + savedTier.getName());
        return toResponse(savedTier);
    }

    @Transactional(readOnly = true)
    public VolumeDiscountResult resolve(BigDecimal itemSubtotal, int itemCount, boolean couponApplied) {
        BigDecimal safeSubtotal = itemSubtotal == null
                ? BigDecimal.ZERO
                : itemSubtotal.setScale(2, RoundingMode.HALF_UP);

        VolumeDiscountTier matchedTier = tierRepository.findByActiveTrueOrderByDiscountPercentDescIdDesc()
                .stream()
                .filter(tier -> matches(tier, safeSubtotal, itemCount))
                .max(Comparator
                        .comparing(VolumeDiscountTier::getDiscountPercent)
                        .thenComparing(tier -> tier.getMinOrderValue() == null ? BigDecimal.ZERO : tier.getMinOrderValue())
                        .thenComparing(tier -> tier.getMinItemCount() == null ? 0 : tier.getMinItemCount()))
                .orElse(null);

        if (matchedTier == null || safeSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return VolumeDiscountResult.none();
        }

        BigDecimal percent = matchedTier.getDiscountPercent().setScale(2, RoundingMode.HALF_UP);
        String label = matchedTier.getName() + " (" + percent.stripTrailingZeros().toPlainString() + "%)";
        if (couponApplied) {
            return new VolumeDiscountResult(
                    false,
                    percent,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    label,
                    "Mengenrabatt wäre möglich, wurde aber nicht mit dem Gutschein kombiniert."
            );
        }

        BigDecimal amount = safeSubtotal
                .multiply(percent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);

        return new VolumeDiscountResult(true, percent, amount, label, null);
    }

    private VolumeDiscountTier loadTier(Long tierId) {
        return tierRepository.findById(tierId)
                .orElseThrow(() -> new EntityNotFoundException("Rabattstaffel nicht gefunden: " + tierId));
    }

    private void applyRequest(VolumeDiscountTier tier, VolumeDiscountTierRequest request) {
        BigDecimal minOrderValue = normalizeMoney(request.minOrderValue());
        Integer minItemCount = request.minItemCount();
        BigDecimal discountPercent = request.discountPercent() == null
                ? null
                : request.discountPercent().setScale(2, RoundingMode.HALF_UP);

        if (minOrderValue == null && minItemCount == null) {
            throw new IllegalArgumentException("Bitte Mindestbestellwert oder Mindestmenge angeben.");
        }
        if (discountPercent == null
                || discountPercent.compareTo(BigDecimal.ZERO) <= 0
                || discountPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Der Rabattwert muss zwischen 0,01 und 100 Prozent liegen.");
        }

        tier.setName(request.name().trim());
        tier.setMinOrderValue(minOrderValue);
        tier.setMinItemCount(minItemCount);
        tier.setDiscountPercent(discountPercent);
        tier.setActive(Boolean.TRUE.equals(request.active()));
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean matches(VolumeDiscountTier tier, BigDecimal subtotal, int itemCount) {
        boolean valueMatches = tier.getMinOrderValue() != null
                && subtotal.compareTo(tier.getMinOrderValue()) >= 0;
        boolean quantityMatches = tier.getMinItemCount() != null
                && itemCount >= tier.getMinItemCount();
        return valueMatches || quantityMatches;
    }

    private VolumeDiscountTierResponse toResponse(VolumeDiscountTier tier) {
        return new VolumeDiscountTierResponse(
                tier.getId(),
                tier.getName(),
                tier.getMinOrderValue(),
                tier.getMinItemCount(),
                tier.getDiscountPercent(),
                tier.isActive(),
                tier.getCreatedAt(),
                tier.getUpdatedAt()
        );
    }

    private void recordAction(User actingUser, String action, VolumeDiscountTier tier, String details) {
        auditLogService.record(
                actingUser,
                action,
                "VolumeDiscountTier",
                tier.getId(),
                AuditInitiator.ADMIN,
                details
        );
    }

    public record VolumeDiscountResult(
            boolean applied,
            BigDecimal percent,
            BigDecimal amount,
            String label,
            String exclusionMessage
    ) {
        public static VolumeDiscountResult none() {
            return new VolumeDiscountResult(
                    false,
                    null,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    null,
                    null
            );
        }
    }
}
