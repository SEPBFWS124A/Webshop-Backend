package de.fhdw.webshop.address;

public record PostalCodeLookupResponse(
        String postalCode,
        String city
) {}
