package de.fhdw.webshop.standingorder;

import de.fhdw.webshop.standingorder.dto.CreateStandingOrderRequest;
import de.fhdw.webshop.standingorder.dto.StandingOrderResponse;
import de.fhdw.webshop.standingorder.dto.UpdateStandingOrderRequest;
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
@RequestMapping("/api/standing-orders")
@RequiredArgsConstructor
public class StandingOrderController {

    private final StandingOrderService standingOrderService;

    /** US #51 — View own standing orders. */
    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<StandingOrderResponse>> listStandingOrders(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(standingOrderService.listForCustomer(currentUser.getId()));
    }

    /** US #51 — Create a new standing order. */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<StandingOrderResponse> createStandingOrder(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateStandingOrderRequest createStandingOrderRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(standingOrderService.create(currentUser, createStandingOrderRequest));
    }

    /** US #53 — Update interval or items of a standing order. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<StandingOrderResponse> updateStandingOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateStandingOrderRequest updateStandingOrderRequest) {
        return ResponseEntity.ok(standingOrderService.update(id, currentUser.getId(), updateStandingOrderRequest));
    }

    /** US #52 — Cancel a standing order. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> cancelStandingOrder(@PathVariable Long id,
                                                    @AuthenticationPrincipal User currentUser) {
        standingOrderService.cancel(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<StandingOrderResponse> toggleStandingOrder(
        @PathVariable Long id,
        @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(standingOrderService.toggleActive(id, currentUser.getId()));
    }
}
