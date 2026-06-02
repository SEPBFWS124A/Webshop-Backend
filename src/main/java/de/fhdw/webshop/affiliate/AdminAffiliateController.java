package de.fhdw.webshop.affiliate;

import de.fhdw.webshop.affiliate.dto.AdminAffiliateApplicationResponse;
import de.fhdw.webshop.affiliate.dto.AdminAffiliateListItem;
import de.fhdw.webshop.affiliate.dto.AffiliateDashboardStats;
import de.fhdw.webshop.affiliate.dto.CommissionRateRequest;
import de.fhdw.webshop.affiliate.dto.ReviewDecisionRequest;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/affiliate")
@RequiredArgsConstructor
public class AdminAffiliateController {

    private final AffiliateService affiliateService;

    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<AdminAffiliateApplicationResponse>> listApplications() {
        return ResponseEntity.ok(affiliateService.getAllApplications());
    }

    @PutMapping("/applications/{id}/approve")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Void> approve(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewDecisionRequest request,
            @AuthenticationPrincipal User reviewer) {
        affiliateService.approveApplication(id, reviewer, request != null ? request.note() : null);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/applications/{id}/reject")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Void> reject(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewDecisionRequest request,
            @AuthenticationPrincipal User reviewer) {
        affiliateService.rejectApplication(id, reviewer, request != null ? request.note() : null);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/applications/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revoke(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin) {
        affiliateService.revokeAffiliate(id, admin);
        return ResponseEntity.noContent().build();
    }

    // ── Affiliate-Gesamtübersicht ─────────────────────────────────────────────

    @GetMapping("/affiliates")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<AdminAffiliateListItem>> listAffiliates() {
        return ResponseEntity.ok(affiliateService.getAllAffiliates());
    }

    @GetMapping("/affiliates/{id}/stats")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<AffiliateDashboardStats> getAffiliateStats(@PathVariable Long id) {
        return ResponseEntity.ok(affiliateService.getAffiliateStatsForAdmin(id));
    }

    @PutMapping("/affiliates/{id}/commission-rate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateCommissionRate(
            @PathVariable Long id,
            @Valid @RequestBody CommissionRateRequest request) {
        affiliateService.updateCommissionRate(id, request.commissionRate());
        return ResponseEntity.noContent().build();
    }
}
