package de.fhdw.webshop.warehouse;

import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.warehouse.dto.AdvanceWarehouseOrderRequest;
import de.fhdw.webshop.warehouse.dto.WarehouseOrderResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(warehouseService.listOrders(status));
    }

    @PutMapping("/orders/{id}/advance")
    @PreAuthorize("hasAnyRole('WAREHOUSE_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<WarehouseOrderResponse> advanceOrder(
            @PathVariable Long id,
            @RequestBody(required = false) AdvanceWarehouseOrderRequest request) {
        return ResponseEntity.ok(warehouseService.advanceOrder(
                id,
                request != null ? request.truckIdentifier() : null
        ));
    }
}
