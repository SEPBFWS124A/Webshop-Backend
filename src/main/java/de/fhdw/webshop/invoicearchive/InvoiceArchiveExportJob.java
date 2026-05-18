package de.fhdw.webshop.invoicearchive;

import de.fhdw.webshop.invoicearchive.dto.InvoiceArchiveExportResponse;
import java.time.Instant;

class InvoiceArchiveExportJob {

    private final String exportId;
    private final String fileName;
    private final int selectedCount;
    private final Instant createdAt;
    private volatile String status = "PROCESSING";
    private volatile String message = "ZIP-Datei wird vorbereitet.";
    private volatile Instant completedAt;
    private volatile byte[] zipContent;

    InvoiceArchiveExportJob(String exportId, String fileName, int selectedCount) {
        this.exportId = exportId;
        this.fileName = fileName;
        this.selectedCount = selectedCount;
        this.createdAt = Instant.now();
    }

    void markReady(byte[] zipContent) {
        this.zipContent = zipContent;
        this.status = "READY";
        this.message = "ZIP-Datei steht zum Download bereit.";
        this.completedAt = Instant.now();
    }

    void markFailed(String message) {
        this.status = "FAILED";
        this.message = message;
        this.completedAt = Instant.now();
    }

    boolean isReady() {
        return "READY".equals(status) && zipContent != null;
    }

    byte[] zipContent() {
        return zipContent;
    }

    String fileName() {
        return fileName;
    }

    InvoiceArchiveExportResponse toResponse() {
        return new InvoiceArchiveExportResponse(
                exportId,
                status,
                selectedCount,
                fileName,
                isReady() ? "/api/invoice-archive/exports/" + exportId + "/download" : null,
                message,
                createdAt,
                completedAt);
    }
}
