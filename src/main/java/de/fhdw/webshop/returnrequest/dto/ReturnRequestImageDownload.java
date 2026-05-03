package de.fhdw.webshop.returnrequest.dto;

public record ReturnRequestImageDownload(
        String fileName,
        String contentType,
        byte[] data
) {}
