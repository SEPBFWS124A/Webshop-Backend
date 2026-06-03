package de.fhdw.webshop.pricehistory;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Öffentliche View: nur changedAt + newPrice (für Gäste und Kund:innen).
 */
public record PriceHistoryPublicDto(
        Instant changedAt,
        BigDecimal newPrice
) {}
