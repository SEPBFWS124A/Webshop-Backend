package de.fhdw.webshop.pricealert;

import java.math.BigDecimal;

public record UpdatePriceAlertRequest(
        BigDecimal targetPrice,
        Boolean active,
        Boolean notifyByEmail
) {}

