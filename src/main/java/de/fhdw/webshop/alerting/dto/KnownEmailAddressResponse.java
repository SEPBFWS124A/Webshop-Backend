package de.fhdw.webshop.alerting.dto;

public record KnownEmailAddressResponse(Long id, String label, String email, boolean isDefault) {}
