package de.fhdw.webshop.reservation;

import de.fhdw.webshop.reservation.dto.AvailabilityNotificationResponse;
import de.fhdw.webshop.reservation.dto.InventoryStockResponse;
import de.fhdw.webshop.reservation.dto.StockReservationResponse;
import de.fhdw.webshop.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StockReservationController {

    private final StockReservationService stockReservationService;

    @GetMapping("/api/admin/stock-reservations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StockReservationResponse>> listOpenReservations() {
        return ResponseEntity.ok(stockReservationService.listActiveReservations());
    }

    @GetMapping("/api/warehouse/stock-overview")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<InventoryStockResponse>> listInventoryStock() {
        return ResponseEntity.ok(stockReservationService.listInventoryStock());
    }

    @PostMapping("/api/products/{productId}/availability-notifications")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AvailabilityNotificationResponse> requestAvailabilityNotification(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long productId) {
        return ResponseEntity.ok(stockReservationService.requestAvailabilityNotification(currentUser, productId));
    }
}
