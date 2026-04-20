package de.fhdw.webshop.customer;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerCouponResponse(
        Long id,
        String code,
        BigDecimal discountPercent,
        LocalDate validUntil,
        boolean used
) {}
