package de.fhdw.webshop.invoicearchive.dto;

public record InvoiceArchiveRequesterResponse(
        Long id,
        String username,
        String email,
        String customerNumber,
        boolean currentAccount
) {}
