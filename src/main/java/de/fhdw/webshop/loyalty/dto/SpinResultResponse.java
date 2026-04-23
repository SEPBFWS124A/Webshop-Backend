package de.fhdw.webshop.loyalty.dto;

import java.util.List;

/** Ergebnis eines Glücksrad-Spins. */
public record SpinResultResponse(
        String prizeType,
        String prizeLabel,
        String couponCode,
        List<HighlightProduct> highlightProducts
) {
    public record HighlightProduct(Long id, String name, String category, java.math.BigDecimal price) {}
}
