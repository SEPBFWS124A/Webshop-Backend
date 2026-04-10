package de.fhdw.webshop.discount.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DiscountResponse(
        Long id,
        Long customerId,
        Long productId,
        BigDecimal discountPercent,
        LocalDate validFrom,
        LocalDate validUntil
) {}
