package de.fhdw.webshop.invoicearchive;

import de.fhdw.webshop.invoicearchive.dto.InvoiceArchiveExportRequest;
import de.fhdw.webshop.invoicearchive.dto.InvoiceArchiveExportResponse;
import de.fhdw.webshop.invoicearchive.dto.InvoiceArchiveOrderDetailResponse;
import de.fhdw.webshop.invoicearchive.dto.InvoiceArchiveOrderResponse;
import de.fhdw.webshop.invoicearchive.dto.InvoiceArchiveRequesterResponse;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoice-archive")
@RequiredArgsConstructor
public class InvoiceArchiveController {

    private final InvoiceArchiveService invoiceArchiveService;

    @GetMapping("/requesters")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<InvoiceArchiveRequesterResponse>> listRequesters(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(invoiceArchiveService.listRequesters(currentUser));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<InvoiceArchiveOrderResponse>> listInvoices(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long requesterId) {
        return ResponseEntity.ok(invoiceArchiveService.listInvoices(currentUser, from, to, requesterId));
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<InvoiceArchiveOrderDetailResponse> getInvoiceDetail(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(invoiceArchiveService.getInvoiceDetail(currentUser, orderId));
    }

    @GetMapping(value = "/orders/{orderId}/download", produces = "application/pdf")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<byte[]> downloadInvoice(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long orderId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + invoiceArchiveService.getInvoicePdfFileName(currentUser, orderId) + "\"")
                .body(invoiceArchiveService.getInvoicePdf(currentUser, orderId));
    }

    @PostMapping("/exports")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<InvoiceArchiveExportResponse> createExport(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody InvoiceArchiveExportRequest request) {
        return ResponseEntity.accepted().body(invoiceArchiveService.createExport(currentUser, request.orderIds()));
    }

    @GetMapping("/exports/{exportId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<InvoiceArchiveExportResponse> getExport(@PathVariable String exportId) {
        return ResponseEntity.ok(invoiceArchiveService.getExport(exportId));
    }

    @GetMapping(value = "/exports/{exportId}/download", produces = "application/zip")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<byte[]> downloadExport(@PathVariable String exportId) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + invoiceArchiveService.getExportFileName(exportId) + "\"")
                .body(invoiceArchiveService.getExportContent(exportId));
    }
}
