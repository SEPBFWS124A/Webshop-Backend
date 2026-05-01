package de.fhdw.webshop.tradein;

import de.fhdw.webshop.tradein.dto.CreateTradeInRequest;
import de.fhdw.webshop.tradein.dto.TradeInResponse;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TradeInController {

    private final TradeInService tradeInService;

    /** US #199 — Customer submits a trade-in request for a previously ordered item. */
    @PostMapping("/api/trade-in")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TradeInResponse> createTradeIn(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateTradeInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tradeInService.createTradeIn(currentUser, request));
    }

    /** US #199 — Customer views their own trade-in requests. */
    @GetMapping("/api/trade-in/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<TradeInResponse>> listMyTradeIns(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(tradeInService.listForCustomer(currentUser.getId()));
    }

    /** US #199 — Employee/Admin views all trade-in requests. */
    @GetMapping("/api/admin/trade-in")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<TradeInResponse>> listAllTradeIns() {
        return ResponseEntity.ok(tradeInService.listAll());
    }

    /** US #199 — Employee approves a trade-in and releases the coupon/shop credit. */
    @PutMapping("/api/admin/trade-in/{id}/approve")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<TradeInResponse> approveTradeIn(@PathVariable Long id) {
        return ResponseEntity.ok(tradeInService.approveTradeIn(id));
    }

    /** US #199 — Employee rejects a trade-in request. */
    @PutMapping("/api/admin/trade-in/{id}/reject")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<TradeInResponse> rejectTradeIn(@PathVariable Long id) {
        return ResponseEntity.ok(tradeInService.rejectTradeIn(id));
    }
}
