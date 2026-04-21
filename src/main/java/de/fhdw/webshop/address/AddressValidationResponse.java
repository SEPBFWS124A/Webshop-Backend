package de.fhdw.webshop.address;

public record AddressValidationResponse(
        boolean valid,
        String displayLabel,
        String street,
        String postalCode,
        String city,
        String country,
        String message
) {}
