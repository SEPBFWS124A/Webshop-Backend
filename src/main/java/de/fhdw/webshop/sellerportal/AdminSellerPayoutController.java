package de.fhdw.webshop.sellerportal;

import de.fhdw.webshop.sellerportal.dto.SellerPayoutCorrectionRequest;
import de.fhdw.webshop.sellerportal.dto.SellerPayoutDetailResponse;
import de.fhdw.webshop.sellerportal.dto.SellerPayoutReviewRequest;
import de.fhdw.webshop.sellerportal.dto.SellerPayoutSummaryResponse;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/seller-payouts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES_EMPLOYEE','ADMIN')")
public class AdminSellerPayoutController {

    private final SellerPortalService sellerPortalService;

    @GetMapping
    public ResponseEntity<List<SellerPayoutSummaryResponse>> listPayouts(
            @RequestParam(required = false) Long sellerProfileId,
            @RequestParam(required = false) SellerPayoutStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(sellerPortalService.listAdminPayouts(sellerProfileId, status, from, to));
    }

    @GetMapping("/{payoutId}")
    public ResponseEntity<SellerPayoutDetailResponse> getPayout(@PathVariable Long payoutId) {
        return ResponseEntity.ok(sellerPortalService.getAdminPayoutDetail(payoutId));
    }

    @PatchMapping("/{payoutId}/review")
    public ResponseEntity<SellerPayoutDetailResponse> moveToReview(
            @PathVariable Long payoutId,
            @RequestBody(required = false) SellerPayoutReviewRequest request,
            @AuthenticationPrincipal User employee) {
        return ResponseEntity.ok(sellerPortalService.movePayoutToReview(payoutId, request, employee));
    }

    @PatchMapping("/{payoutId}/approve")
    public ResponseEntity<SellerPayoutDetailResponse> approve(
            @PathVariable Long payoutId,
            @RequestBody(required = false) SellerPayoutReviewRequest request,
            @AuthenticationPrincipal User employee) {
        return ResponseEntity.ok(sellerPortalService.approvePayout(payoutId, request, employee));
    }

    @PatchMapping("/{payoutId}/correct")
    public ResponseEntity<SellerPayoutDetailResponse> correct(
            @PathVariable Long payoutId,
            @Valid @RequestBody SellerPayoutCorrectionRequest request,
            @AuthenticationPrincipal User employee) {
        return ResponseEntity.ok(sellerPortalService.correctPayout(payoutId, request, employee));
    }

    @PatchMapping("/{payoutId}/paid-out")
    public ResponseEntity<SellerPayoutDetailResponse> markPaidOut(
            @PathVariable Long payoutId,
            @RequestBody(required = false) SellerPayoutReviewRequest request,
            @AuthenticationPrincipal User employee) {
        return ResponseEntity.ok(sellerPortalService.markPayoutAsPaidOut(payoutId, request, employee));
    }
}
