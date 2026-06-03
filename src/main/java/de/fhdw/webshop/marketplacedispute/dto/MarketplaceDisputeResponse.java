package de.fhdw.webshop.marketplacedispute.dto;

import de.fhdw.webshop.marketplacedispute.MarketplaceDisputeReason;
import de.fhdw.webshop.marketplacedispute.MarketplaceDisputeStatus;
import java.time.Instant;
import java.util.List;

public record MarketplaceDisputeResponse(
        Long id,
        Long orderId,
        String orderNumber,
        Long orderItemId,
        Long productId,
        String productName,
        String customerName,
        String sellerName,
        MarketplaceDisputeReason reason,
        MarketplaceDisputeStatus status,
        String description,
        boolean payoutBlocked,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        List<String> imageUrls,
        List<MarketplaceDisputeHistoryResponse> history
) {
}
