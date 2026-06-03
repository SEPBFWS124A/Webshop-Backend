package de.fhdw.webshop.warehouse;

import de.fhdw.webshop.address.AddressLookupService;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.subscription.SubscriptionService;
import de.fhdw.webshop.user.DeliveryAddressRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.warehouse.dto.DepartureReadinessResponse;
import de.fhdw.webshop.warehouse.dto.StartDepartureResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive tests for the new truck departure endpoints:
 * - POST /api/warehouse/trucks/{truckId}/departure-readiness
 * - POST /api/warehouse/trucks/{truckId}/start-departure
 *
 * Covers edge cases:
 * - DELIVERED orders from previous trips should be ignored
 * - PACKED_IN_WAREHOUSE orders (not loaded) are removed on departure
 * - Internal transfers mixed with normal orders
 * - Various route combinations
 * - Already departed trucks
 * - Empty trucks
 */
@DisplayName("Truck Departure Readiness & Start-Departure - Edge Cases")
class WarehouseTruckStartDepartureTest {

    // =========================================================================
    // departure-readiness endpoint tests
    // =========================================================================

    @Nested
    @DisplayName("POST /departure-readiness")
    class DepartureReadinessTests {

        @Test
        @DisplayName("Should report ready when all active orders are IN_TRUCK")
        void readyWhenAllOrdersInTruck() {
            TestContext ctx = newContext();
            Order o1 = inTruckOrder("ORD-001", "LKW-T1");
            Order o2 = inTruckOrder("ORD-002", "LKW-T1");
            WarehouseTruck truck = loadedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(o1, o2));

            DepartureReadinessResponse response = ctx.service.checkDepartureReadiness("LKW-T1");

            assertThat(response.readyToDepart()).isTrue();
            assertThat(response.totalOrdersOnTruck()).isEqualTo(2);
            assertThat(response.readyOrders()).isEqualTo(2);
            assertThat(response.notReadyOrders()).isZero();
            assertThat(response.issues()).isEmpty();
        }

        @Test
        @DisplayName("Should ignore DELIVERED orders from previous trips")
        void ignoresDeliveredOrdersFromPreviousTrips() {
            TestContext ctx = newContext();
            Order active = inTruckOrder("ORD-ACTIVE", "LKW-T1");
            Order delivered = deliveredOrder("ORD-OLD", "LKW-T1");
            WarehouseTruck truck = loadedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(active, delivered));

            DepartureReadinessResponse response = ctx.service.checkDepartureReadiness("LKW-T1");

            assertThat(response.readyToDepart()).isTrue();
            assertThat(response.totalOrdersOnTruck()).isEqualTo(1); // Only active order counted
            assertThat(response.readyOrders()).isEqualTo(1);
            assertThat(response.issues()).isEmpty();
        }

        @Test
        @DisplayName("Should report not ready when some orders are PACKED_IN_WAREHOUSE")
        void notReadyWithPackedOrders() {
            TestContext ctx = newContext();
            Order ready = inTruckOrder("ORD-001", "LKW-T1");
            Order notReady = packedOrder("ORD-002", "LKW-T1");
            WarehouseTruck truck = loadedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(ready, notReady));

            DepartureReadinessResponse response = ctx.service.checkDepartureReadiness("LKW-T1");

            assertThat(response.readyToDepart()).isFalse();
            assertThat(response.totalOrdersOnTruck()).isEqualTo(2);
            assertThat(response.readyOrders()).isEqualTo(1);
            assertThat(response.notReadyOrders()).isEqualTo(1);
            assertThat(response.issues()).anyMatch(issue -> issue.contains("ORD-002"));
        }

        @Test
        @DisplayName("Should report issue when truck already departed")
        void alreadyDeparted() {
            TestContext ctx = newContext();
            Order shipped = shippedOrder("ORD-001", "LKW-T1");
            WarehouseTruck truck = departedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(shipped));

            DepartureReadinessResponse response = ctx.service.checkDepartureReadiness("LKW-T1");

            assertThat(response.readyToDepart()).isFalse();
            assertThat(response.issues()).anyMatch(issue -> issue.contains("abgefahren"));
        }

        @Test
        @DisplayName("Should report issue when truck has no active orders")
        void noActiveOrders() {
            TestContext ctx = newContext();
            Order delivered = deliveredOrder("ORD-OLD", "LKW-T1");
            WarehouseTruck truck = loadedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(delivered));

            DepartureReadinessResponse response = ctx.service.checkDepartureReadiness("LKW-T1");

            assertThat(response.readyToDepart()).isFalse();
            assertThat(response.totalOrdersOnTruck()).isZero();
            assertThat(response.issues()).anyMatch(issue -> issue.contains("Keine aktiven"));
        }

        @Test
        @DisplayName("Should handle mix of DELIVERED (old) + IN_TRUCK (active) + CANCELLED (old)")
        void mixedHistoricalAndActive() {
            TestContext ctx = newContext();
            Order active1 = inTruckOrder("ORD-A1", "LKW-T1");
            Order active2 = inTruckOrder("ORD-A2", "LKW-T1");
            Order delivered = deliveredOrder("ORD-PREV-1", "LKW-T1");
            Order cancelled = cancelledOrder("ORD-CANCELLED", "LKW-T1");
            WarehouseTruck truck = loadedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(active1, active2, delivered, cancelled));

            DepartureReadinessResponse response = ctx.service.checkDepartureReadiness("LKW-T1");

            assertThat(response.readyToDepart()).isTrue();
            assertThat(response.totalOrdersOnTruck()).isEqualTo(2);
            assertThat(response.readyOrders()).isEqualTo(2);
        }
    }

    // =========================================================================
    // start-departure endpoint tests
    // =========================================================================

    @Nested
    @DisplayName("POST /start-departure")
    class StartDepartureTests {

        @Test
        @DisplayName("Happy path: all orders IN_TRUCK, departs successfully")
        void happyPathAllInTruck() {
            TestContext ctx = newContext();
            Order o1 = inTruckOrder("ORD-001", "LKW-T1");
            Order o2 = inTruckOrder("ORD-002", "LKW-T1");
            WarehouseTruck truck = loadedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(o1, o2));
            when(ctx.orderRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ctx.truckRepo.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

            StartDepartureResponse response = ctx.service.startDeparture("LKW-T1", warehouseEmployee());

            assertThat(response.success()).isTrue();
            assertThat(response.shippedOrderCount()).isEqualTo(2);
            assertThat(response.skippedOrderCount()).isZero();
            assertThat(response.departureTime()).isNotNull();
            assertThat(o1.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(o2.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(truck.getStatus()).isEqualTo(TruckStatus.DEPARTED);
        }

        @Test
        @DisplayName("DELIVERED orders from past trips are ignored, departure succeeds")
        void ignoresDeliveredFromPastTrips() {
            TestContext ctx = newContext();
            Order active = inTruckOrder("ORD-ACTIVE", "LKW-T1");
            Order oldDelivered = deliveredOrder("ORD-WH-1018", "LKW-T1"); // The exact error scenario
            WarehouseTruck truck = loadedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(active, oldDelivered));
            when(ctx.orderRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ctx.truckRepo.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

            StartDepartureResponse response = ctx.service.startDeparture("LKW-T1", warehouseEmployee());

            assertThat(response.success()).isTrue();
            assertThat(response.shippedOrderCount()).isEqualTo(1);
            assertThat(response.skippedOrderCount()).isZero();
            assertThat(active.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            // DELIVERED order stays unchanged
            assertThat(oldDelivered.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("PACKED_IN_WAREHOUSE orders are removed from truck on departure")
        void removesNotLoadedOrders() {
            TestContext ctx = newContext();
            Order loaded = inTruckOrder("ORD-LOADED", "LKW-T1");
            Order notLoaded = packedOrder("ORD-REJECTED", "LKW-T1");
            WarehouseTruck truck = loadedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(loaded, notLoaded));
            when(ctx.orderRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ctx.truckRepo.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

            StartDepartureResponse response = ctx.service.startDeparture("LKW-T1", warehouseEmployee());

            assertThat(response.success()).isTrue();
            assertThat(response.shippedOrderCount()).isEqualTo(1);
            assertThat(response.skippedOrderCount()).isEqualTo(1);
            assertThat(response.skippedOrders()).hasSize(1);
            assertThat(response.skippedOrders().get(0).orderNumber()).isEqualTo("ORD-REJECTED");
            assertThat(loaded.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            // Not-loaded order has its truck assignment cleared
            assertThat(notLoaded.getTruckIdentifier()).isNull();
            assertThat(notLoaded.getTruckAssignedAt()).isNull();
            assertThat(notLoaded.getRouteOptimizationId()).isNull();
            assertThat(notLoaded.getStatus()).isEqualTo(OrderStatus.PACKED_IN_WAREHOUSE);
        }

        @Test
        @DisplayName("Fails when truck has already departed")
        void failsWhenAlreadyDeparted() {
            TestContext ctx = newContext();
            Order shipped = shippedOrder("ORD-001", "LKW-T1");
            WarehouseTruck truck = departedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(shipped));

            assertThatThrownBy(() -> ctx.service.startDeparture("LKW-T1", warehouseEmployee()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already departed");
        }

        @Test
        @DisplayName("Fails when no IN_TRUCK orders exist (only PACKED_IN_WAREHOUSE)")
        void failsWhenNoLoadedOrders() {
            TestContext ctx = newContext();
            Order packed = packedOrder("ORD-001", "LKW-T1");
            WarehouseTruck truck = loadedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(packed));

            assertThatThrownBy(() -> ctx.service.startDeparture("LKW-T1", warehouseEmployee()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no loaded orders");
        }

        @Test
        @DisplayName("Fails when only DELIVERED orders exist (all from previous trips)")
        void failsWhenOnlyDeliveredOrders() {
            TestContext ctx = newContext();
            Order delivered = deliveredOrder("ORD-OLD", "LKW-T1");
            WarehouseTruck truck = loadedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(delivered));

            assertThatThrownBy(() -> ctx.service.startDeparture("LKW-T1", warehouseEmployee()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no loaded orders");
        }

        @Test
        @DisplayName("Internal transfer orders are shipped together with normal orders")
        void internalTransfersShippedTogether() {
            TestContext ctx = newContext();
            Order normalOrder = inTruckOrder("ORD-NORMAL", "LKW-T1");
            Order transferOrder = inTruckOrder("TRANSFER-001", "LKW-T1");
            transferOrder.setDeliveryStreet("Warehouse B");
            transferOrder.setDeliveryCity("Internal Transfer");
            WarehouseTruck truck = loadedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(normalOrder, transferOrder));
            when(ctx.orderRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ctx.truckRepo.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

            StartDepartureResponse response = ctx.service.startDeparture("LKW-T1", warehouseEmployee());

            assertThat(response.success()).isTrue();
            assertThat(response.shippedOrderCount()).isEqualTo(2);
            assertThat(normalOrder.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(transferOrder.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("Large batch: 20 orders from different cities all IN_TRUCK")
        void largeBatchDifferentCities() {
            TestContext ctx = newContext();
            List<Order> orders = new ArrayList<>();
            String[] cities = {"Berlin", "Hamburg", "München", "Köln", "Frankfurt"};
            for (int i = 0; i < 20; i++) {
                Order order = inTruckOrder("ORD-BATCH-" + i, "LKW-BIG");
                order.setDeliveryCity(cities[i % cities.length]);
                orders.add(order);
            }
            WarehouseTruck truck = loadedTruck("LKW-BIG");
            truck.setCapacityOrders(30);

            when(ctx.truckRepo.findByTruckIdentifier("LKW-BIG")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-BIG")).thenReturn(orders);
            when(ctx.orderRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ctx.truckRepo.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

            StartDepartureResponse response = ctx.service.startDeparture("LKW-BIG", warehouseEmployee());

            assertThat(response.success()).isTrue();
            assertThat(response.shippedOrderCount()).isEqualTo(20);
            assertThat(response.shippedOrders()).hasSize(20);
            orders.forEach(o -> assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED));
        }

        @Test
        @DisplayName("Mixed scenario: IN_TRUCK + PACKED + DELIVERED + CANCELLED")
        void complexMixedScenario() {
            TestContext ctx = newContext();
            Order loaded1 = inTruckOrder("ORD-LOADED-1", "LKW-MIX");
            Order loaded2 = inTruckOrder("ORD-LOADED-2", "LKW-MIX");
            Order packed = packedOrder("ORD-PACKED", "LKW-MIX");
            Order delivered = deliveredOrder("ORD-DELIVERED-OLD", "LKW-MIX");
            Order cancelled = cancelledOrder("ORD-CANCELLED", "LKW-MIX");
            WarehouseTruck truck = loadedTruck("LKW-MIX");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-MIX")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-MIX"))
                    .thenReturn(List.of(loaded1, loaded2, packed, delivered, cancelled));
            when(ctx.orderRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ctx.truckRepo.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

            StartDepartureResponse response = ctx.service.startDeparture("LKW-MIX", warehouseEmployee());

            assertThat(response.success()).isTrue();
            assertThat(response.shippedOrderCount()).isEqualTo(2); // Only IN_TRUCK
            assertThat(response.skippedOrderCount()).isEqualTo(1); // PACKED removed
            assertThat(loaded1.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(loaded2.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(packed.getTruckIdentifier()).isNull(); // Removed from truck
            assertThat(delivered.getStatus()).isEqualTo(OrderStatus.DELIVERED); // Untouched
            assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED); // Untouched
        }

        @Test
        @DisplayName("Truck departure time is set correctly")
        void departureTimeIsSet() {
            TestContext ctx = newContext();
            Order order = inTruckOrder("ORD-001", "LKW-T1");
            WarehouseTruck truck = loadedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(order));
            when(ctx.orderRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ctx.truckRepo.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

            Instant before = Instant.now();
            StartDepartureResponse response = ctx.service.startDeparture("LKW-T1", warehouseEmployee());

            assertThat(response.departureTime()).isAfterOrEqualTo(before);
            assertThat(truck.getDepartureTime()).isNotNull();
            assertThat(truck.getCurrentWarehouseLocation()).isNull();
            assertThat(truck.getStatus()).isEqualTo(TruckStatus.DEPARTED);
        }

        @Test
        @DisplayName("Audit log is recorded on departure")
        void auditLogRecorded() {
            TestContext ctx = newContext();
            Order order = inTruckOrder("ORD-001", "LKW-T1");
            WarehouseTruck truck = loadedTruck("LKW-T1");
            truck.setId(42L);

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(order));
            when(ctx.orderRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ctx.truckRepo.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

            ctx.service.startDeparture("LKW-T1", warehouseEmployee());

            verify(ctx.auditLogService).record(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Response contains delivery city information for route display")
        void responseContainsCityInfo() {
            TestContext ctx = newContext();
            Order o1 = inTruckOrder("ORD-001", "LKW-T1");
            o1.setDeliveryCity("Düsseldorf");
            o1.setDeliveryPostalCode("40217");
            Order o2 = inTruckOrder("ORD-002", "LKW-T1");
            o2.setDeliveryCity("Meerbusch");
            o2.setDeliveryPostalCode("40667");
            WarehouseTruck truck = loadedTruck("LKW-T1");

            when(ctx.truckRepo.findByTruckIdentifier("LKW-T1")).thenReturn(Optional.of(truck));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-T1"))
                    .thenReturn(List.of(o1, o2));
            when(ctx.orderRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ctx.truckRepo.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

            StartDepartureResponse response = ctx.service.startDeparture("LKW-T1", warehouseEmployee());

            assertThat(response.shippedOrders()).hasSize(2);
            assertThat(response.shippedOrders().get(0).deliveryCity()).isEqualTo("Düsseldorf");
            assertThat(response.shippedOrders().get(1).deliveryCity()).isEqualTo("Meerbusch");
        }
    }

    // =========================================================================
    // Tests for the fixed ensureTruckReadyForDeparture (via advance endpoint)
    // =========================================================================

    @Nested
    @DisplayName("Fixed advance endpoint (IN_TRUCK -> SHIPPED)")
    class FixedAdvanceEndpointTests {

        @Test
        @DisplayName("DELIVERED orders from past trips no longer block departure via advance")
        void deliveredOrdersNoLongerBlockAdvance() {
            TestContext ctx = newContext();
            Order activeOrder = inTruckOrder("ORD-WH-1012", "LKW-2024-101");
            Order pastDelivered = deliveredOrder("ORD-WH-1018", "LKW-2024-101");
            WarehouseTruck truck = loadedTruck("LKW-2024-101");

            when(ctx.orderRepo.findById(activeOrder.getId())).thenReturn(Optional.of(activeOrder));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-2024-101"))
                    .thenReturn(List.of(activeOrder, pastDelivered));
            when(ctx.orderRepo.findByTruckIdentifierAndStatus("LKW-2024-101", OrderStatus.IN_TRUCK))
                    .thenReturn(List.of(activeOrder));
            when(ctx.truckRepo.findByTruckIdentifier("LKW-2024-101"))
                    .thenReturn(Optional.of(truck));
            when(ctx.orderRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ctx.truckRepo.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

            var request = new de.fhdw.webshop.warehouse.dto.AdvanceWarehouseOrderRequest(
                    OrderStatus.SHIPPED, null, null, null, null);
            var response = ctx.service.advanceOrderWithNextStatus(activeOrder.getId(), request, warehouseEmployee());

            assertThat(response.success()).isTrue();
            assertThat(activeOrder.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            // Past delivered order is untouched
            assertThat(pastDelivered.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("CANCELLED orders from past are ignored during departure check")
        void cancelledOrdersIgnored() {
            TestContext ctx = newContext();
            Order activeOrder = inTruckOrder("ORD-ACTIVE", "LKW-X");
            Order cancelled = cancelledOrder("ORD-CANCEL", "LKW-X");
            WarehouseTruck truck = loadedTruck("LKW-X");

            when(ctx.orderRepo.findById(activeOrder.getId())).thenReturn(Optional.of(activeOrder));
            when(ctx.orderRepo.findByTruckIdentifierOrderByCreatedAtAsc("LKW-X"))
                    .thenReturn(List.of(activeOrder, cancelled));
            when(ctx.orderRepo.findByTruckIdentifierAndStatus("LKW-X", OrderStatus.IN_TRUCK))
                    .thenReturn(List.of(activeOrder));
            when(ctx.truckRepo.findByTruckIdentifier("LKW-X"))
                    .thenReturn(Optional.of(truck));
            when(ctx.orderRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ctx.truckRepo.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

            var request = new de.fhdw.webshop.warehouse.dto.AdvanceWarehouseOrderRequest(
                    OrderStatus.SHIPPED, null, null, null, null);
            var response = ctx.service.advanceOrderWithNextStatus(activeOrder.getId(), request, warehouseEmployee());

            assertThat(response.success()).isTrue();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static TestContext newContext() {
        OrderRepository orderRepo = mock(OrderRepository.class);
        DeliveryAddressRepository deliveryAddressRepo = mock(DeliveryAddressRepository.class);
        AddressLookupService addressLookupService = mock(AddressLookupService.class);
        de.fhdw.webshop.product.ProductRepository productRepo = mock(de.fhdw.webshop.product.ProductRepository.class);
        WarehouseLocationRepository warehouseLocationRepo = mock(WarehouseLocationRepository.class);
        WarehouseProductStockRepository stockRepo = mock(WarehouseProductStockRepository.class);
        WarehouseTruckRepository truckRepo = mock(WarehouseTruckRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        WarehouseStockBalanceService balanceService = mock(WarehouseStockBalanceService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        WarehouseService service = new WarehouseService(
                orderRepo, deliveryAddressRepo, addressLookupService, productRepo,
                warehouseLocationRepo, stockRepo, truckRepo, auditLogService,
                eventPublisher, balanceService, subscriptionService
        );

        when(orderRepo.findByTruckIdentifierOrderByCreatedAtAsc(anyString())).thenReturn(List.of());

        return new TestContext(service, orderRepo, truckRepo, auditLogService);
    }

    private static Order inTruckOrder(String orderNumber, String truckId) {
        return createOrder(orderNumber, OrderStatus.IN_TRUCK, truckId);
    }

    private static Order packedOrder(String orderNumber, String truckId) {
        return createOrder(orderNumber, OrderStatus.PACKED_IN_WAREHOUSE, truckId);
    }

    private static Order shippedOrder(String orderNumber, String truckId) {
        return createOrder(orderNumber, OrderStatus.SHIPPED, truckId);
    }

    private static Order deliveredOrder(String orderNumber, String truckId) {
        Order order = createOrder(orderNumber, OrderStatus.DELIVERED, truckId);
        order.setDeliveredAt(Instant.now().minusSeconds(86400));
        return order;
    }

    private static Order cancelledOrder(String orderNumber, String truckId) {
        return createOrder(orderNumber, OrderStatus.CANCELLED, truckId);
    }

    private static Order createOrder(String orderNumber, OrderStatus status, String truckId) {
        WarehouseLocation warehouse = createWarehouse();
        Order order = new Order();
        order.setId(System.nanoTime());
        order.setOrderNumber(orderNumber);
        order.setCustomerName("Test Customer");
        order.setCustomerEmail("test@example.com");
        order.setDeliveryStreet("Teststraße 1");
        order.setDeliveryCity("Bielefeld");
        order.setDeliveryPostalCode("33602");
        order.setDeliveryCountry("Deutschland");
        order.setCreatedAt(Instant.now());
        order.setStatus(status);
        order.setTruckIdentifier(truckId);
        order.setTruckAssignedAt(Instant.now().minusSeconds(3600));
        order.setFulfillmentWarehouse(warehouse);
        order.setItems(List.of());
        return order;
    }

    private static WarehouseTruck loadedTruck(String identifier) {
        WarehouseTruck truck = new WarehouseTruck();
        truck.setTruckIdentifier(identifier);
        truck.setOriginWarehouseLocation(createWarehouse());
        truck.setCurrentWarehouseLocation(createWarehouse());
        truck.setStatus(TruckStatus.LOADED);
        truck.setCapacityOrders(10);
        return truck;
    }

    private static WarehouseTruck departedTruck(String identifier) {
        WarehouseTruck truck = loadedTruck(identifier);
        truck.setStatus(TruckStatus.DEPARTED);
        truck.setDepartureTime(Instant.now().minusSeconds(3600));
        truck.setCurrentWarehouseLocation(null);
        return truck;
    }

    private static WarehouseLocation createWarehouse() {
        WarehouseLocation location = new WarehouseLocation();
        location.setId(10L);
        location.setCode("MAIN");
        location.setName("Hauptlager Bielefeld");
        location.setStreet("Lagerstraße 1");
        location.setPostalCode("33602");
        location.setCity("Bielefeld");
        location.setCountry("Deutschland");
        location.setMainLocation(true);
        location.setActive(true);
        return location;
    }

    private static User warehouseEmployee() {
        User user = new User();
        user.setId(5L);
        user.setUsername("warehouse.employee");
        user.setEmail("warehouse@example.com");
        return user;
    }

    private record TestContext(
            WarehouseService service,
            OrderRepository orderRepo,
            WarehouseTruckRepository truckRepo,
            AuditLogService auditLogService
    ) {}
}


