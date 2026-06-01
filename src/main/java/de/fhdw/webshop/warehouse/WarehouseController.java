package de.fhdw.webshop.warehouse;

import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.warehouse.dto.AdvanceOrderResponse;
import de.fhdw.webshop.warehouse.dto.AdvanceWarehouseOrderRequest;
import de.fhdw.webshop.warehouse.dto.AutoAssignTruckIdentifiersResponse;
import de.fhdw.webshop.warehouse.dto.CompletePackingResponse;
import de.fhdw.webshop.warehouse.dto.PickOrderItemRequest;
import de.fhdw.webshop.warehouse.dto.WarehouseLocationResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseOrderResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseTruckResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseTransferRequest;
import de.fhdw.webshop.warehouse.dto.WarehouseTransferResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    /**
     * Returns warehouse orders filtered by one or more statuses.
     * The {@code status} parameter accepts a single value or a comma-separated list,
     * e.g. {@code ?status=CONFIRMED} or {@code ?status=PACKED_IN_WAREHOUSE,IN_TRUCK,SHIPPED}.
     */
    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<WarehouseOrderResponse>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long warehouseLocationId) {
        List<OrderStatus> statuses = null;
        if (status != null && !status.isBlank()) {
            statuses = Arrays.stream(status.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(OrderStatus::valueOf)
                    .toList();
        }
        return ResponseEntity.ok(warehouseService.listOrders(statuses, warehouseLocationId));
    }

    @GetMapping("/locations")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<WarehouseLocationResponse>> listLocations() {
        return ResponseEntity.ok(warehouseService.listLocations());
    }

    @GetMapping("/trucks")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<WarehouseTruckResponse>> listTrucks(
            @RequestParam(required = false) Long warehouseLocationId) {
        return ResponseEntity.ok(warehouseService.listTrucks(warehouseLocationId));
    }

    @GetMapping("/trucks/{truckIdentifier}/orders")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<WarehouseOrderResponse>> listTruckOrders(@PathVariable String truckIdentifier) {
        return ResponseEntity.ok(warehouseService.listOrdersForTruck(truckIdentifier));
    }

    @PutMapping("/orders/{id}/warehouse")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<WarehouseOrderResponse> updateFulfillmentWarehouse(
            @PathVariable Long id,
            @RequestBody AdvanceWarehouseOrderRequest request) {
        return ResponseEntity.ok(warehouseService.updateFulfillmentWarehouse(
                id,
                request != null ? request.warehouseLocationId() : null
        ));
    }

    @PutMapping("/orders/{id}/truck")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<WarehouseOrderResponse> updateTruckIdentifier(
            @PathVariable Long id,
            @RequestBody AdvanceWarehouseOrderRequest request) {
        return ResponseEntity.ok(warehouseService.updateTruckIdentifier(
                id,
                request != null ? request.truckIdentifier() : null
        ));
    }

    @DeleteMapping("/orders/{orderId}/truck")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<WarehouseOrderResponse> removeTruckIdentifier(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(warehouseService.removeTruckIdentifier(orderId, currentUser));
    }

    /**
     * Advances an order through the driver/logistics workflow.
     * Accepted input statuses: PACKED_IN_WAREHOUSE, IN_TRUCK, SHIPPED.
     * The {@code nextStatus} field in the request body is mandatory.
     *
     * <ul>
     *   <li>PACKED_IN_WAREHOUSE → IN_TRUCK  (requires {@code truckIdentifier})</li>
     *   <li>IN_TRUCK → SHIPPED              (batch: all orders on the same truck)</li>
     *   <li>SHIPPED → DELIVERED             (optional lat/lon coordinates)</li>
     * </ul>
     */
    @PutMapping("/orders/{id}/advance")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<AdvanceOrderResponse> advanceOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser,
            @RequestBody(required = false) AdvanceWarehouseOrderRequest request) {
        return ResponseEntity.ok(warehouseService.advanceOrderWithNextStatus(id, request, currentUser));
    }

    @PatchMapping("/orders/{orderId}/items/{itemId}/pick")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<WarehouseOrderResponse> updateItemPickState(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody PickOrderItemRequest request) {
        return ResponseEntity.ok(warehouseService.updateItemPickState(orderId, itemId, request, currentUser));
    }

    @PostMapping("/orders/{orderId}/complete-packing")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<CompletePackingResponse> completePacking(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(warehouseService.completePacking(orderId, currentUser));
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<WarehouseTransferResponse> transferStock(
            @Valid @RequestBody WarehouseTransferRequest request) {
        return ResponseEntity.ok(warehouseService.transferStock(request));
    }

    @PostMapping("/orders/auto-assign-trucks")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<AutoAssignTruckIdentifiersResponse> autoAssignTruckIdentifiers() {
        return ResponseEntity.ok(warehouseService.autoAssignTruckIdentifiers());
    }
}
