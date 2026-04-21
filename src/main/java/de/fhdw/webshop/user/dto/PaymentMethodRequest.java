package de.fhdw.webshop.user.dto;

import de.fhdw.webshop.user.PaymentMethodType;
import jakarta.validation.constraints.NotNull;

public record PaymentMethodRequest(
        @NotNull PaymentMethodType methodType,
        String maskedDetails,
        String cardNumber,
        Integer expiryMonth,
        Integer expiryYear,
        String cvv
) {}
