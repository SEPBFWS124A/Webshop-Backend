package de.fhdw.webshop.tradein.dto;

import de.fhdw.webshop.tradein.TradeInCondition;
import jakarta.validation.constraints.NotNull;

public record CreateTradeInRequest(
        @NotNull Long orderItemId,
        @NotNull TradeInCondition condition
) {}
