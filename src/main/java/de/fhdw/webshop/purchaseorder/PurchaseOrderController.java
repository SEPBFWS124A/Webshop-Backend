package de.fhdw.webshop.purchaseorder;

import de.fhdw.webshop.purchaseorder.dto.CreatePurchaseOrderRequest;
import de.fhdw.webshop.purchaseorder.dto.PurchaseOrderResponse;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    /** #124 — List all purchase orders. */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<PurchaseOrderResponse>> listAll() {
        return ResponseEntity.ok(purchaseOrderService.listAll());
    }

    /** #124 — Create a new purchase order (includes AI quantity suggestion). */
    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<PurchaseOrderResponse> create(
            @Valid @RequestBody CreatePurchaseOrderRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(purchaseOrderService.create(request, currentUser));
    }

    /** #124 — Get AI-suggested reorder quantity for a product. */
    @GetMapping("/suggest-quantity/{productId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Map<String, Integer>> suggestQuantity(@PathVariable Long productId) {
        int qty = purchaseOrderService.suggestQuantityForProduct(productId);
        return ResponseEntity.ok(Map.of("suggestedQuantity", qty));
    }

    /** #124 — Confirm goods receipt and increase product stock. */
    @PutMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<PurchaseOrderResponse> confirmReceipt(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(purchaseOrderService.confirmReceipt(id, currentUser));
    }
}
