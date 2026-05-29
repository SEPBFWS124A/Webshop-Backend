package de.fhdw.webshop.sellerportal;

import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.sellerportal.dto.SellerDashboardResponse;
import de.fhdw.webshop.sellerportal.dto.SellerOrderSummaryResponse;
import de.fhdw.webshop.sellerportal.dto.SellerPayoutDetailResponse;
import de.fhdw.webshop.sellerportal.dto.SellerPayoutSummaryResponse;
import de.fhdw.webshop.user.User;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller-portal")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerPortalController {

    private final SellerPortalService sellerPortalService;

    @GetMapping("/dashboard")
    public ResponseEntity<SellerDashboardResponse> getDashboard(@AuthenticationPrincipal User sellerUser) {
        return ResponseEntity.ok(sellerPortalService.getDashboard(sellerUser));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<SellerOrderSummaryResponse>> listOrders(
            @AuthenticationPrincipal User sellerUser,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(sellerPortalService.listSellerOrders(sellerUser, status, from, to));
    }

    @GetMapping("/payouts")
    public ResponseEntity<List<SellerPayoutSummaryResponse>> listPayouts(
            @AuthenticationPrincipal User sellerUser,
            @RequestParam(required = false) SellerPayoutStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(sellerPortalService.listSellerPayouts(sellerUser, status, from, to));
    }

    @GetMapping("/payouts/{payoutId}")
    public ResponseEntity<SellerPayoutDetailResponse> getPayout(
            @AuthenticationPrincipal User sellerUser,
            @PathVariable Long payoutId) {
        return ResponseEntity.ok(sellerPortalService.getSellerPayoutDetail(sellerUser, payoutId));
    }

    @GetMapping(value = "/payouts/{payoutId}/download.csv", produces = "text/csv")
    public ResponseEntity<byte[]> downloadPayoutCsv(
            @AuthenticationPrincipal User sellerUser,
            @PathVariable Long payoutId) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"seller-payout-" + payoutId + ".csv\"")
                .body(sellerPortalService.downloadSellerPayoutCsv(sellerUser, payoutId));
    }

    @GetMapping(value = "/payouts/{payoutId}/download.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPayoutPdf(
            @AuthenticationPrincipal User sellerUser,
            @PathVariable Long payoutId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"seller-payout-" + payoutId + ".pdf\"")
                .body(sellerPortalService.downloadSellerPayoutPdf(sellerUser, payoutId));
    }
}
