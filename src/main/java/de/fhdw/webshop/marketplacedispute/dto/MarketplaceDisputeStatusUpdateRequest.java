package de.fhdw.webshop.marketplacedispute.dto;

import de.fhdw.webshop.marketplacedispute.MarketplaceDisputeStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MarketplaceDisputeStatusUpdateRequest(
        @NotNull(message = "Bitte einen Status auswaehlen.")
        MarketplaceDisputeStatus status,

        @Size(max = 1000, message = "Die Notiz darf maximal 1000 Zeichen lang sein.")
        String note
) {
}
