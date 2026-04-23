package de.fhdw.webshop.loyalty;

import de.fhdw.webshop.loyalty.dto.LoyaltyStatusResponse;
import de.fhdw.webshop.loyalty.dto.SpinResultResponse;
import de.fhdw.webshop.loyalty.dto.WheelPrizeResponse;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loyalty")
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    /** US-1, US-2, US-3 — Vollständiger Loyalty-Status des eingeloggten Kunden. */
    @GetMapping("/status")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<LoyaltyStatusResponse> getStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(loyaltyService.getStatus(user));
    }

    /** US-3 — Glücksrad drehen (max. 1× pro 24 h). */
    @PostMapping("/spin")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<SpinResultResponse> spin(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(loyaltyService.spin(user));
    }

    /** US-2 — Umsatz-Rabattgutschein einmalig abrufen. */
    @PostMapping("/claim-volume-discount")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, String>> claimVolumeDiscount(@AuthenticationPrincipal User user) {
        String code = loyaltyService.claimVolumeDiscount(user);
        return ResponseEntity.ok(Map.of("couponCode", code));
    }

    /** Admin/Sales — Glücksrad-Konfiguration lesen. */
    @GetMapping("/wheel-prizes")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_EMPLOYEE')")
    public ResponseEntity<List<WheelPrizeResponse>> listPrizes() {
        return ResponseEntity.ok(loyaltyService.listPrizes());
    }
}
