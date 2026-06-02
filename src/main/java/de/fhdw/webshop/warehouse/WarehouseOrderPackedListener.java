package de.fhdw.webshop.warehouse;

import de.fhdw.webshop.admin.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseOrderPackedListener {

    private final WarehouseService warehouseService;
    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPacked(WarehouseOrderPackedEvent event) {
        if (event.orderId() == null) {
            return;
        }

        warehouseService.autoAssignTruckIdentifiers();
        auditLogService.recordSystemAction(
                "TRUCK_ASSIGNMENT_TRIGGERED",
                "Order",
                event.orderId(),
                "Truck assignment triggered after packing completion"
        );
        // Route planning integration is represented as a trigger point for downstream systems.
        auditLogService.recordSystemAction(
                "ROUTE_REPLANNED",
                "Order",
                event.orderId(),
                "Route recalculation triggered after packing completion"
        );
        log.info("Post-pack workflow executed for order {}", event.orderId());
    }
}

