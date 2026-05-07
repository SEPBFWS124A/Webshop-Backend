package de.fhdw.webshop.pickup.dto;

public record PickupStoreResponse(
        Long id,
        String name,
        String street,
        String postalCode,
        String city,
        String country,
        String openingHours
) {}
