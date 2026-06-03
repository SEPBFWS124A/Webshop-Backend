package de.fhdw.webshop.pricehistory;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Admin-View: alle Felder inkl. oldPrice, changeReason, changedByName.
 */
public record PriceHistoryAdminDto(
        Long id,
        Instant changedAt,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        PriceChangeReason changeReason,
        String changedByName
) {}
