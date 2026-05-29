package de.fhdw.webshop.sellerportal.dto;

import java.util.List;

public record SellerPayoutDetailResponse(
        SellerPayoutSummaryResponse payout,
        List<SellerPayoutItemResponse> items
) {
}
