package de.fhdw.webshop.address;

public record GeocodedAddressResponse(
        String displayLabel,
        String street,
        String postalCode,
        String city,
        String country,
        double latitude,
        double longitude
) {}
