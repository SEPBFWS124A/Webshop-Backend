package de.fhdw.webshop.discount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class VolumeDiscountPolicy {

    public static final String DISCOUNT_TYPE = "VOLUME";

    private static final List<VolumeDiscountTier> TIERS = List.of(
            new VolumeDiscountTier(25, new BigDecimal("1000.00"), new BigDecimal("10.00")),
            new VolumeDiscountTier(10, new BigDecimal("500.00"), new BigDecimal("5.00"))
    );

    private VolumeDiscountPolicy() {
    }

    public static VolumeDiscountResult resolve(BigDecimal itemSubtotal, int itemCount, boolean couponApplied) {
        BigDecimal safeSubtotal = itemSubtotal == null
                ? BigDecimal.ZERO
                : itemSubtotal.setScale(2, RoundingMode.HALF_UP);
        VolumeDiscountTier matchedTier = TIERS.stream()
                .filter(tier -> tier.matches(safeSubtotal, itemCount))
                .findFirst()
                .orElse(null);

        if (matchedTier == null || safeSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return VolumeDiscountResult.none();
        }

        String label = "Mengenrabatt " + matchedTier.percent().stripTrailingZeros().toPlainString() + "%";
        if (couponApplied) {
            return new VolumeDiscountResult(
                    false,
                    matchedTier.percent(),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    label,
                    "Mengenrabatt waere moeglich, wurde aber nicht mit dem Gutschein kombiniert."
            );
        }

        BigDecimal amount = safeSubtotal
                .multiply(matchedTier.percent().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);

        return new VolumeDiscountResult(true, matchedTier.percent(), amount, label, null);
    }

    private record VolumeDiscountTier(int minItemCount, BigDecimal minSubtotal, BigDecimal percent) {
        private boolean matches(BigDecimal subtotal, int itemCount) {
            return itemCount >= minItemCount || subtotal.compareTo(minSubtotal) >= 0;
        }
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
