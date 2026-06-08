package de.fhdw.webshop.marketplacedispute.dto;

import de.fhdw.webshop.marketplacedispute.MarketplaceDisputeReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateMarketplaceDisputeRequest(
        @NotNull(message = "Bitte einen Konfliktgrund auswaehlen.")
        MarketplaceDisputeReason reason,

        @Size(max = 2000, message = "Die Beschreibung darf maximal 2000 Zeichen lang sein.")
        String description,

        @Size(max = 3, message = "Es duerfen maximal 3 Nachweisbilder hinterlegt werden.")
        List<@Size(max = 1000, message = "Eine Bildreferenz darf maximal 1000 Zeichen lang sein.") String> imageUrls
) {
}
