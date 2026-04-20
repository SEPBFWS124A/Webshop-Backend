package de.fhdw.webshop.customer;

public record CustomerBusinessInfoResponse(
        String companyName,
        String industry,
        String companySize
) {}
