package de.fhdw.webshop.invoicearchive.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record InvoiceArchiveExportRequest(
        @NotEmpty List<Long> orderIds
) {}
