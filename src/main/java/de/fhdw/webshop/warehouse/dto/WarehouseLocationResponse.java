package de.fhdw.webshop.warehouse.dto;

public record WarehouseLocationResponse(
        Long id,
        String code,
        String name,
        String street,
        String postalCode,
        String city,
        String country,
        boolean mainLocation
) {}
