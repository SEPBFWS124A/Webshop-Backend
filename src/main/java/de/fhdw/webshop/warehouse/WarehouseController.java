package de.fhdw.webshop.warehouse;

import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.warehouse.dto.AdvanceWarehouseOrderRequest;
import de.fhdw.webshop.warehouse.dto.AutoAssignTruckIdentifiersResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseLocationResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseOrderResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseTransferRequest;
import de.fhdw.webshop.warehouse.dto.WarehouseTransferResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<WarehouseOrderResponse>> listOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long warehouseLocationId) {
        return ResponseEntity.ok(warehouseService.listOrders(status, warehouseLocationId));
    }

    @GetMapping("/locations")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<WarehouseLocationResponse>> listLocations() {
        return ResponseEntity.ok(warehouseService.listLocations());
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

    @PutMapping("/orders/{id}/advance")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<WarehouseOrderResponse> advanceOrder(
            @PathVariable Long id,
            @RequestBody(required = false) AdvanceWarehouseOrderRequest request) {
        return ResponseEntity.ok(warehouseService.advanceOrder(
                id,
                request != null ? request.truckIdentifier() : null,
                request != null ? request.warehouseLocationId() : null
        ));
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
