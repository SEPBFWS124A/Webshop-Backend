package de.fhdw.webshop.sellerportal.dto;

import de.fhdw.webshop.sellerportal.SellerPayoutStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SellerPayoutSummaryResponse(
        Long id,
        String payoutNumber,
        Long sellerId,
        String sellerDisplayName,
        LocalDate periodStart,
        LocalDate periodEnd,
        SellerPayoutStatus status,
        BigDecimal grossSalesAmount,
        BigDecimal discountAmount,
        BigDecimal commissionAmount,
        BigDecimal feeAmount,
        BigDecimal openReturnHoldbackAmount,
        BigDecimal settledReturnAmount,
        BigDecimal manualAdjustmentAmount,
        BigDecimal netPayoutAmount,
        boolean payoutBlocked,
        Instant createdAt,
        Instant approvedAt,
        Instant paidOutAt,
        Instant correctedAt,
        String correctionReason,
        String adminNote,
        String processedBy
) {
}
