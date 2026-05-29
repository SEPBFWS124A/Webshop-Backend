package de.fhdw.webshop.sellerportal.dto;

import java.math.BigDecimal;

public record SellerProfileResponse(
        Long id,
        String displayName,
        BigDecimal commissionRate,
        String payoutIban
) {
}
