package de.fhdw.webshop.invoicearchive.dto;

import java.time.Instant;

public record InvoiceArchiveExportResponse(
        String exportId,
        String status,
        int selectedCount,
        String fileName,
        String downloadUrl,
        String message,
        Instant createdAt,
        Instant completedAt
) {}
