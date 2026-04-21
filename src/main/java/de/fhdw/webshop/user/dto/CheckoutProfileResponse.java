package de.fhdw.webshop.user.dto;

public record CheckoutProfileResponse(
        DeliveryAddressResponse deliveryAddress,
        PaymentMethodResponse paymentMethod
) {}
