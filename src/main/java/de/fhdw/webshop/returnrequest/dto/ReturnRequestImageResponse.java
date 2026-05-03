package de.fhdw.webshop.returnrequest.dto;

public record ReturnRequestImageResponse(
        Long id,
        String fileName,
        String contentType,
        long sizeBytes,
        String downloadUrl
) {}
