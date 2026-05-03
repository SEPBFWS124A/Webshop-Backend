package de.fhdw.webshop.returnrequest.dto;

public record ReturnLabelAddressResponse(
        String name,
        String street,
        String postalCode,
        String city,
        String country
) {}
