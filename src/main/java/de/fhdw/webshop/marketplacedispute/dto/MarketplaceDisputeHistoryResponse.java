package de.fhdw.webshop.marketplacedispute.dto;

import de.fhdw.webshop.marketplacedispute.MarketplaceDisputeStatus;
import java.time.Instant;

public record MarketplaceDisputeHistoryResponse(
        Long id,
        MarketplaceDisputeStatus status,
        String changedBy,
        Instant changedAt,
        String note
) {
}
