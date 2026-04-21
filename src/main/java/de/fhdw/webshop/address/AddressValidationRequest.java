package de.fhdw.webshop.address;

public record AddressValidationRequest(
        String street,
        String city,
        String postalCode,
        String country
) {}
