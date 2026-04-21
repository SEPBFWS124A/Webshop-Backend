package de.fhdw.webshop.address;

public record AddressSuggestionResponse(
        String displayLabel,
        String street,
        String postalCode,
        String city,
        String country
) {}
