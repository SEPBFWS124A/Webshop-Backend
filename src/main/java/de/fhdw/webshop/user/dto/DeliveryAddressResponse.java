package de.fhdw.webshop.user.dto;

public record DeliveryAddressResponse(
        String street,
        String city,
        String postalCode,
        String country
) {}
