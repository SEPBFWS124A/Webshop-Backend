package de.fhdw.webshop.user.dto;

import de.fhdw.webshop.user.PaymentMethodType;

public record PaymentMethodResponse(
        PaymentMethodType methodType,
        String maskedDetails
) {}
