package de.fhdw.webshop.warehouse;

import de.fhdw.webshop.address.AddressLookupService;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.user.DeliveryAddressRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.warehouse.dto.AdvanceOrderResponse;
import de.fhdw.webshop.warehouse.dto.AdvanceWarehouseOrderRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive tests for truck departure scenarios.
 * Tests various combinations of:
 * - Internal normal orders
 * - Taken/not-taken packages
 * - Orders distributed across different routes
 */
@DisplayName("Warehouse Truck Departure Tests - Comprehensive Routing Scenarios")
class WarehouseTruckDepartureTest {

    /**
     * Scenario 1: Happy path - All orders on the truck are IN_TRUCK status (ready for departure)
     */
    @Test
    @DisplayName("Should depart successfully when all truck orders are IN_TRUCK")
    void testDepartureWithAllOrdersInTruck_success() {
        TestContext context = newContext();
        Order order1 = createOrderWithStatus("ORD-001", OrderStatus.IN_TRUCK, "LKW-001");
        Order order2 = createOrderWithStatus("ORD-002", OrderStatus.IN_TRUCK, "LKW-001");
        WarehouseTruck truck = createTruck("LKW-001");

        when(context.orderRepository.findById(order1.getId())).thenReturn(Optional.of(order1));
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-001", OrderStatus.IN_TRUCK))
                .thenReturn(List.of(order1, order2));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-001"))
                .thenReturn(List.of(order1, order2));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-001"))
                .thenReturn(Optional.of(truck));
        when(context.orderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);
        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(order1.getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.batchUpdatedCount()).isEqualTo(2);
        assertThat(order1.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(context.auditLogService).record(any(), any(), any(), any(), any(), any());
    }

    /**
     * Scenario 2: Error case - Some orders are still PACKED_IN_WAREHOUSE
     */
    @Test
    @DisplayName("Should fail when some orders are still PACKED_IN_WAREHOUSE")
    void testDepartureWithMixedStatuses_fails() {
        TestContext context = newContext();
        Order order1 = createOrderWithStatus("ORD-001", OrderStatus.IN_TRUCK, "LKW-001");
        Order order2 = createOrderWithStatus("ORD-002", OrderStatus.PACKED_IN_WAREHOUSE, "LKW-001");
        WarehouseTruck truck = createTruck("LKW-001");

        when(context.orderRepository.findById(order1.getId())).thenReturn(Optional.of(order1));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-001"))
                .thenReturn(List.of(order1, order2));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-001"))
                .thenReturn(Optional.of(truck));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);

        assertThatThrownBy(() -> context.service.advanceOrderWithNextStatus(order1.getId(), request, warehouseEmployee(5L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot depart")
                .hasMessageContaining("not ready")
                .hasMessageContaining("PACKED_IN_WAREHOUSE");
    }

    /**
     * Scenario 3: Orders distributed across different routes but only one truck being departed
     * - Some orders might be on different trucks or different routes
     * - Only orders on the departing truck should be checked
     */
    @Test
    @DisplayName("Should only check orders for the specific truck being departed")
    void testDepartureChecksOnlyTruckSpecificOrders() {
        TestContext context = newContext();
        Order order1 = createOrderWithStatus("ORD-001", OrderStatus.IN_TRUCK, "LKW-001");
        Order order2 = createOrderWithStatus("ORD-002", OrderStatus.IN_TRUCK, "LKW-001");
        // This order is on a different truck (should not affect LKW-001 departure)
        Order order3 = createOrderWithStatus("ORD-003", OrderStatus.IN_TRUCK, "LKW-002");

        WarehouseTruck truck1 = createTruck("LKW-001");

        when(context.orderRepository.findById(order1.getId())).thenReturn(Optional.of(order1));
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-001", OrderStatus.IN_TRUCK))
                .thenReturn(List.of(order1, order2));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-001"))
                .thenReturn(List.of(order1, order2));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-001"))
                .thenReturn(Optional.of(truck1));
        when(context.orderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);
        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(order1.getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.batchUpdatedCount()).isEqualTo(2);
        // Only orders on LKW-001 should be shipped, not order3
        assertThat(order1.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order3.getStatus()).isEqualTo(OrderStatus.IN_TRUCK); // unchanged
    }

    /**
     * Scenario 4: Internal transfer orders mixed with normal orders
     * - Some orders are internal transfers (between warehouses)
     * - These should be included in the batch if they have IN_TRUCK status
     */
    @Test
    @DisplayName("Should handle internal transfers on the same truck")
    void testDepartureWithInternalTransferOrders() {
        TestContext context = newContext();
        Order normalOrder = createOrderWithStatus("ORD-NORMAL", OrderStatus.IN_TRUCK, "LKW-001");
        Order internalOrder = createInternalTransferOrder("TRANSFER-001", OrderStatus.IN_TRUCK, "LKW-001");

        WarehouseTruck truck = createTruck("LKW-001");

        when(context.orderRepository.findById(normalOrder.getId())).thenReturn(Optional.of(normalOrder));
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-001", OrderStatus.IN_TRUCK))
                .thenReturn(List.of(normalOrder, internalOrder));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-001"))
                .thenReturn(List.of(normalOrder, internalOrder));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-001"))
                .thenReturn(Optional.of(truck));
        when(context.orderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);
        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(normalOrder.getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.batchUpdatedCount()).isEqualTo(2);
        assertThat(normalOrder.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(internalOrder.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    /**
     * Scenario 5: Partial pickup - Some orders were not taken on the truck
     * - Orders removed from truck should not block departure
     * - Only currently assigned orders matter
     */
    @Test
    @DisplayName("Should allow departure when previously assigned orders were removed")
    void testDepartureAfterOrderWasRemoved() {
        TestContext context = newContext();
        Order order1 = createOrderWithStatus("ORD-001", OrderStatus.IN_TRUCK, "LKW-001");
        WarehouseTruck truck = createTruck("LKW-001");

        // Simulate that this truck previously had order2, but it was removed
        // (order2 is now not assigned to this truck, so it shouldn't appear in findByTruckIdentifier)

        when(context.orderRepository.findById(order1.getId())).thenReturn(Optional.of(order1));
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-001", OrderStatus.IN_TRUCK))
                .thenReturn(List.of(order1)); // Only one order now
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-001"))
                .thenReturn(List.of(order1)); // Only one order
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-001"))
                .thenReturn(Optional.of(truck));
        when(context.orderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);
        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(order1.getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.batchUpdatedCount()).isEqualTo(1);
        assertThat(order1.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    /**
     * Scenario 6: Multiple routes, orders are regrouped
     * - Initial assignment might have had orders for multiple routes
     * - But route optimization regrouped them, so now all are on one truck for one route
     */
    @Test
    @DisplayName("Should handle route optimization that regrouped orders")
    void testDepartureAfterRouteOptimizationRegroup() {
        TestContext context = newContext();
        // Orders from different cities but now on the same truck after optimization
        Order order1 = createOrderWithStatus("ORD-BER-001", OrderStatus.IN_TRUCK, "LKW-001");
        order1.setDeliveryCity("Berlin");
        order1.setDeliveryPostalCode("10115");

        Order order2 = createOrderWithStatus("ORD-BER-002", OrderStatus.IN_TRUCK, "LKW-001");
        order2.setDeliveryCity("Berlin");
        order2.setDeliveryPostalCode("10119");

        Order order3 = createOrderWithStatus("ORD-BER-003", OrderStatus.IN_TRUCK, "LKW-001");
        order3.setDeliveryCity("Berlin");
        order3.setDeliveryPostalCode("10179");

        WarehouseTruck truck = createTruck("LKW-001");
        truck.setRouteOptimizationId("route-optimization-berlin-cluster");

        when(context.orderRepository.findById(order1.getId())).thenReturn(Optional.of(order1));
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-001", OrderStatus.IN_TRUCK))
                .thenReturn(List.of(order1, order2, order3));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-001"))
                .thenReturn(List.of(order1, order2, order3));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-001"))
                .thenReturn(Optional.of(truck));
        when(context.orderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);
        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(order1.getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.batchUpdatedCount()).isEqualTo(3);
        assertThat(order1.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order3.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    /**
     * Scenario 7: Large batch departure with many orders on different stops
     * All orders are properly loaded (IN_TRUCK status)
     */
    @Test
    @DisplayName("Should handle large batch departure with 10+ orders")
    void testLargeBatchDeparture() {
        TestContext context = newContext();
        List<Order> orders = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            Order order = createOrderWithStatus("ORD-BATCH-" + i, OrderStatus.IN_TRUCK, "LKW-MEGA");
            order.setDeliveryCity("City" + (i % 5));
            orders.add(order);
        }

        WarehouseTruck truck = createTruck("LKW-MEGA");
        truck.setCapacityOrders(20);

        when(context.orderRepository.findById(orders.get(0).getId())).thenReturn(Optional.of(orders.get(0)));
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-MEGA", OrderStatus.IN_TRUCK))
                .thenReturn(orders);
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-MEGA"))
                .thenReturn(orders);
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-MEGA"))
                .thenReturn(Optional.of(truck));
        when(context.orderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);
        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(orders.get(0).getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.batchUpdatedCount()).isEqualTo(15);
        orders.forEach(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED));
    }

    /**
     * Scenario 8: Empty truck - Should correctly reject empty truck departure
     */
    @Test
    @DisplayName("Should fail when truck has no assigned orders")
    void testDepartureWithNoAssignedOrders_fails() {
        TestContext context = newContext();
        Order order = createOrderWithStatus("ORD-EMPTY", OrderStatus.IN_TRUCK, "LKW-EMPTY");
        WarehouseTruck truck = createTruck("LKW-EMPTY");

        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-EMPTY"))
                .thenReturn(List.of()); // Empty!
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-EMPTY"))
                .thenReturn(Optional.of(truck));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);

        assertThatThrownBy(() -> context.service.advanceOrderWithNextStatus(order.getId(), request, warehouseEmployee(5L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no assigned orders");
    }

    /**
     * Scenario 9: One order ready, others already SHIPPED from previous attempt
     * - Should fail because not all are IN_TRUCK
     */
    @Test
    @DisplayName("Should fail when some orders were already shipped in previous departure")
    void testDepartureFailsWhenSomeAlreadyShipped() {
        TestContext context = newContext();
        Order order1 = createOrderWithStatus("ORD-FIRST", OrderStatus.IN_TRUCK, "LKW-RETRY");
        Order order2 = createOrderWithStatus("ORD-SECOND", OrderStatus.SHIPPED, "LKW-RETRY"); // Already shipped!

        WarehouseTruck truck = createTruck("LKW-RETRY");
        truck.setStatus(TruckStatus.DEPARTED);

        when(context.orderRepository.findById(order1.getId())).thenReturn(Optional.of(order1));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-RETRY"))
                .thenReturn(List.of(order1, order2));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-RETRY"))
                .thenReturn(Optional.of(truck));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);

        assertThatThrownBy(() -> context.service.advanceOrderWithNextStatus(order1.getId(), request, warehouseEmployee(5L)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Scenario 10: Different warehouses, but truck is at wrong warehouse
     * - Should still work if orders are from the truck's current warehouse
     */
    @Test
    @DisplayName("Should allow departure when all orders are from truck's current warehouse")
    void testDepartureFromCorrectWarehouse() {
        TestContext context = newContext();
        WarehouseLocation warehouse = createWarehouse(10L, "WH-BERLIN");

        Order order1 = createOrderWithStatus("ORD-001", OrderStatus.IN_TRUCK, "LKW-001");
        order1.setFulfillmentWarehouse(warehouse);

        Order order2 = createOrderWithStatus("ORD-002", OrderStatus.IN_TRUCK, "LKW-001");
        order2.setFulfillmentWarehouse(warehouse);

        WarehouseTruck truck = createTruck("LKW-001");
        truck.setCurrentWarehouseLocation(warehouse);

        when(context.orderRepository.findById(order1.getId())).thenReturn(Optional.of(order1));
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-001", OrderStatus.IN_TRUCK))
                .thenReturn(List.of(order1, order2));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-001"))
                .thenReturn(List.of(order1, order2));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-001"))
                .thenReturn(Optional.of(truck));
        when(context.orderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);
        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(order1.getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.batchUpdatedCount()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private static TestContext newContext() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        DeliveryAddressRepository deliveryAddressRepository = mock(DeliveryAddressRepository.class);
        AddressLookupService addressLookupService = mock(AddressLookupService.class);
        de.fhdw.webshop.product.ProductRepository productRepository =
                mock(de.fhdw.webshop.product.ProductRepository.class);
        WarehouseLocationRepository warehouseLocationRepository = mock(WarehouseLocationRepository.class);
        WarehouseProductStockRepository warehouseProductStockRepository = mock(WarehouseProductStockRepository.class);
        WarehouseTruckRepository warehouseTruckRepository = mock(WarehouseTruckRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        WarehouseStockBalanceService warehouseStockBalanceService = mock(WarehouseStockBalanceService.class);

        WarehouseService service = new WarehouseService(
                orderRepository,
                deliveryAddressRepository,
                addressLookupService,
                productRepository,
                warehouseLocationRepository,
                warehouseProductStockRepository,
                warehouseTruckRepository,
                auditLogService,
                eventPublisher,
                warehouseStockBalanceService
        );

        when(orderRepository.findByTruckIdentifierOrderByCreatedAtAsc(anyString())).thenReturn(List.of());

        return new TestContext(
                service,
                orderRepository,
                warehouseLocationRepository,
                warehouseProductStockRepository,
                warehouseTruckRepository,
                auditLogService,
                eventPublisher
        );
    }

    private static Order createOrderWithStatus(String orderNumber, OrderStatus status, String truckId) {
        WarehouseLocation location = createWarehouse(10L, "WH-MAIN");

        Order order = new Order();
        order.setId(System.nanoTime());
        order.setOrderNumber(orderNumber);
        order.setCustomerName("Test Customer");
        order.setCustomerEmail("test@example.com");
        order.setDeliveryStreet("Main Street 1");
        order.setDeliveryCity("Bielefeld");
        order.setDeliveryPostalCode("33602");
        order.setDeliveryCountry("Germany");
        order.setCreatedAt(Instant.now());
        order.setStatus(status);
        order.setTruckIdentifier(truckId);
        order.setFulfillmentWarehouse(location);
        order.setItems(List.of());
        return order;
    }

    private static Order createInternalTransferOrder(String orderNumber, OrderStatus status, String truckId) {
        Order order = createOrderWithStatus(orderNumber, status, truckId);
        // Mark as internal transfer
        order.setDeliveryStreet("Warehouse Transfer");
        order.setDeliveryCity("Internal");
        return order;
    }

    private static WarehouseTruck createTruck(String identifier) {
        WarehouseTruck truck = new WarehouseTruck();
        truck.setTruckIdentifier(identifier);
        truck.setOriginWarehouseLocation(createWarehouse(10L, "WH-MAIN"));
        truck.setCurrentWarehouseLocation(createWarehouse(10L, "WH-MAIN"));
        truck.setStatus(TruckStatus.LOADED);
        truck.setCapacityOrders(10);
        return truck;
    }

    private static WarehouseLocation createWarehouse(Long id, String code) {
        WarehouseLocation location = new WarehouseLocation();
        location.setId(id);
        location.setCode(code);
        location.setName("Warehouse " + code);
        location.setStreet("Warehouse Street");
        location.setPostalCode("33602");
        location.setCity("Bielefeld");
        location.setCountry("Germany");
        location.setMainLocation(true);
        location.setActive(true);
        return location;
    }

    private static User warehouseEmployee(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("warehouse.employee");
        user.setEmail("warehouse@example.com");
        return user;
    }

    private record TestContext(
            WarehouseService service,
            OrderRepository orderRepository,
            WarehouseLocationRepository warehouseLocationRepository,
            WarehouseProductStockRepository warehouseProductStockRepository,
            WarehouseTruckRepository warehouseTruckRepository,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher
    ) {}
}


