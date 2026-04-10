package de.fhdw.webshop.user.dto;

import jakarta.validation.constraints.NotBlank;

public record DeliveryAddressRequest(
        @NotBlank String street,
        @NotBlank String city,
        @NotBlank String postalCode,
        @NotBlank String country
) {}
