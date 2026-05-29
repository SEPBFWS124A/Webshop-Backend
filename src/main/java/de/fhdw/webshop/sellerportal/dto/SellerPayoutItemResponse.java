package de.fhdw.webshop.sellerportal.dto;

import de.fhdw.webshop.sellerportal.SellerPayoutItemType;
import java.math.BigDecimal;
import java.time.Instant;

public record SellerPayoutItemResponse(
        Long id,
        SellerPayoutItemType type,
        Instant occurredAt,
        String referenceNumber,
        String description,
        String orderNumber,
        String returnNumber,
        int quantity,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal commissionAmount,
        BigDecimal feeAmount,
        BigDecimal returnHoldbackAmount,
        BigDecimal returnSettlementAmount,
        BigDecimal manualAdjustmentAmount,
        BigDecimal netAmount
) {
}
