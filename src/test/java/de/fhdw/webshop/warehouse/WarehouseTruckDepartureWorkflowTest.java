package de.fhdw.webshop.warehouse;

import de.fhdw.webshop.address.AddressLookupService;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.user.DeliveryAddressRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.warehouse.dto.AdvanceOrderResponse;
import de.fhdw.webshop.warehouse.dto.AdvanceWarehouseOrderRequest;
import java.time.Instant;
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
import static org.mockito.Mockito.when;

/**
 * Workflow integration tests for truck departure with realistic scenarios.
 * Tests the complete workflow from packing through loading to departure.
 */
@DisplayName("Warehouse Truck Departure - Workflow Integration Tests")
class WarehouseTruckDepartureWorkflowTest {

    /**
     * Scenario: Complete workflow - one order from confirmation to delivery
     */
    @Test
    @DisplayName("Complete workflow: CONFIRMED -> PACKED -> IN_TRUCK -> SHIPPED -> DELIVERED")
    void testCompleteOrderWorkflow() {
        TestContext context = newContext();
        Order order = createOrder("ORD-WORKFLOW-001");
        WarehouseLocation warehouse = createWarehouse(10L, "WH-MAIN");
        WarehouseTruck truck = createTruck("LKW-WORKFLOW");

        order.setFulfillmentWarehouse(warehouse);
        order.setStatus(OrderStatus.CONFIRMED);

        // Step 1: Pack the order
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(context.orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseProductStockRepository.findByProductIdAndWarehouseLocationId(any(), any()))
                .thenReturn(Optional.empty());

        order.setStatus(OrderStatus.PACKED_IN_WAREHOUSE);

        // Step 2: Assign to truck (IN_TRUCK)
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-WORKFLOW"))
                .thenReturn(Optional.of(truck));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest assignRequest = new AdvanceWarehouseOrderRequest(
                OrderStatus.IN_TRUCK, "LKW-WORKFLOW", null, null, null);
        AdvanceOrderResponse assignResponse = context.service.advanceOrderWithNextStatus(
                order.getId(), assignRequest, warehouseEmployee(5L));

        assertThat(assignResponse.success()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_TRUCK);
        assertThat(order.getTruckIdentifier()).isEqualTo("LKW-WORKFLOW");

        // Step 3: Depart the truck
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-WORKFLOW"))
                .thenReturn(List.of(order));
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-WORKFLOW", OrderStatus.IN_TRUCK))
                .thenReturn(List.of(order));
        when(context.orderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest departRequest = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);
        AdvanceOrderResponse departResponse = context.service.advanceOrderWithNextStatus(
                order.getId(), departRequest, warehouseEmployee(5L));

        assertThat(departResponse.success()).isTrue();
        assertThat(departResponse.batchUpdatedCount()).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(truck.getDepartureTime()).isNotNull();

        // Step 4: Deliver
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-WORKFLOW", OrderStatus.SHIPPED))
                .thenReturn(List.of());
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-WORKFLOW"))
                .thenReturn(Optional.of(truck));

        AdvanceWarehouseOrderRequest deliverRequest = new AdvanceWarehouseOrderRequest(
                OrderStatus.DELIVERED, null, null, 50.1234, 6.6789);
        AdvanceOrderResponse deliverResponse = context.service.advanceOrderWithNextStatus(
                order.getId(), deliverRequest, warehouseEmployee(5L));

        assertThat(deliverResponse.success()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    /**
     * Scenario: Multiple orders on same truck going to different cities
     */
    @Test
    @DisplayName("Multiple orders on same truck, different cities, all ready to depart")
    void testMultipleCitiesSameTruck() {
        TestContext context = newContext();
        Order order1 = createOrderInCity("ORD-MUL-001", "Berlin", "10115");
        Order order2 = createOrderInCity("ORD-MUL-002", "Berlin", "10119");
        Order order3 = createOrderInCity("ORD-MUL-003", "Potsdam", "14467");

        order1.setTruckIdentifier("LKW-MULTI");
        order1.setStatus(OrderStatus.IN_TRUCK);
        order2.setTruckIdentifier("LKW-MULTI");
        order2.setStatus(OrderStatus.IN_TRUCK);
        order3.setTruckIdentifier("LKW-MULTI");
        order3.setStatus(OrderStatus.IN_TRUCK);

        WarehouseTruck truck = createTruck("LKW-MULTI");

        when(context.orderRepository.findById(order1.getId())).thenReturn(Optional.of(order1));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-MULTI"))
                .thenReturn(List.of(order1, order2, order3));
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-MULTI", OrderStatus.IN_TRUCK))
                .thenReturn(List.of(order1, order2, order3));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-MULTI"))
                .thenReturn(Optional.of(truck));
        when(context.orderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);
        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(
                order1.getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.batchUpdatedCount()).isEqualTo(3);
        assertThat(response.message()).contains("3 order(s)");
    }

    /**
     * Scenario: Detailed error message when orders are not ready
     */
    @Test
    @DisplayName("Provides detailed error message showing which orders are not ready")
    void testDetailedErrorMessageForNotReadyOrders() {
        TestContext context = newContext();
        Order order1 = createOrderInCity("ORD-ERR-001", "Berlin", "10115");
        Order order2 = createOrderInCity("ORD-ERR-002", "Berlin", "10119");
        Order order3 = createOrderInCity("ORD-ERR-003", "Potsdam", "14467");

        order1.setTruckIdentifier("LKW-ERROR");
        order1.setStatus(OrderStatus.IN_TRUCK);
        order2.setTruckIdentifier("LKW-ERROR");
        order2.setStatus(OrderStatus.PACKED_IN_WAREHOUSE); // NOT READY!
        order3.setTruckIdentifier("LKW-ERROR");
        order3.setStatus(OrderStatus.PACKED_IN_WAREHOUSE); // NOT READY!

        WarehouseTruck truck = createTruck("LKW-ERROR");

        when(context.orderRepository.findById(order1.getId())).thenReturn(Optional.of(order1));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-ERROR"))
                .thenReturn(List.of(order1, order2, order3));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-ERROR"))
                .thenReturn(Optional.of(truck));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);

        assertThatThrownBy(() -> context.service.advanceOrderWithNextStatus(
                order1.getId(), request, warehouseEmployee(5L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LKW-ERROR")
                .hasMessageContaining("2 order(s)")
                .hasMessageContaining("not ready")
                .hasMessageContaining("ORD-ERR-002")
                .hasMessageContaining("ORD-ERR-003")
                .hasMessageContaining("PACKED_IN_WAREHOUSE");
    }

    /**
     * Scenario: Rejection when trying to add order to already departed truck
     */
    @Test
    @DisplayName("Should reject adding orders to already departed truck")
    void testRejectAssignmentToDepartedTruck() {
        TestContext context = newContext();
        Order order = createOrder("ORD-LATE-001");
        order.setStatus(OrderStatus.PACKED_IN_WAREHOUSE); // Must be in correct status for IN_TRUCK transition
        WarehouseTruck truck = createTruck("LKW-DEPARTED");
        truck.setStatus(TruckStatus.DEPARTED);
        truck.setDepartureTime(Instant.now());

        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-DEPARTED"))
                .thenReturn(Optional.of(truck));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.IN_TRUCK, "LKW-DEPARTED", null, null, null);

        assertThatThrownBy(() -> context.service.advanceOrderWithNextStatus(
                order.getId(), request, warehouseEmployee(5L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already departed");
    }

    /**
     * Scenario: Single order on truck can depart
     */
    @Test
    @DisplayName("Single order on truck can successfully depart")
    void testSingleOrderDeparture() {
        TestContext context = newContext();
        Order order = createOrder("ORD-SINGLE");
        order.setTruckIdentifier("LKW-SINGLE");
        order.setStatus(OrderStatus.IN_TRUCK);

        WarehouseTruck truck = createTruck("LKW-SINGLE");

        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-SINGLE"))
                .thenReturn(List.of(order));
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-SINGLE", OrderStatus.IN_TRUCK))
                .thenReturn(List.of(order));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-SINGLE"))
                .thenReturn(Optional.of(truck));
        when(context.orderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);
        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(
                order.getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.batchUpdatedCount()).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    /**
     * Scenario: 50 orders on one truck all ready
     */
    @Test
    @DisplayName("Large batch of 50 orders on same truck, all ready to depart")
    void testLargeBatch50Orders() {
        TestContext context = newContext();
        List<Order> orders = new java.util.ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Order order = createOrder("ORD-LARGE-" + i);
            order.setTruckIdentifier("LKW-LARGE50");
            order.setStatus(OrderStatus.IN_TRUCK);
            order.setDeliveryCity("City" + (i % 10));
            orders.add(order);
        }

        WarehouseTruck truck = createTruck("LKW-LARGE50");
        truck.setCapacityOrders(100);

        when(context.orderRepository.findById(orders.get(0).getId())).thenReturn(Optional.of(orders.get(0)));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-LARGE50"))
                .thenReturn(orders);
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-LARGE50", OrderStatus.IN_TRUCK))
                .thenReturn(orders);
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-LARGE50"))
                .thenReturn(Optional.of(truck));
        when(context.orderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);
        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(
                orders.get(0).getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.batchUpdatedCount()).isEqualTo(50);
    }

    /**
     * Scenario: Mixed statuses error with truncation of very large error message
     */
    @Test
    @DisplayName("Error message truncates when more than 5 orders are not ready")
    void testErrorMessageTruncationLargeBatch() {
        TestContext context = newContext();
        List<Order> allOrders = new java.util.ArrayList<>();

        // One ready order
        Order readyOrder = createOrder("ORD-READY");
        readyOrder.setTruckIdentifier("LKW-TRUNC");
        readyOrder.setStatus(OrderStatus.IN_TRUCK);
        allOrders.add(readyOrder);

        // 10 not-ready orders
        for (int i = 1; i <= 10; i++) {
            Order order = createOrder("ORD-NOTREADY-" + i);
            order.setTruckIdentifier("LKW-TRUNC");
            order.setStatus(OrderStatus.PACKED_IN_WAREHOUSE);
            allOrders.add(order);
        }

        WarehouseTruck truck = createTruck("LKW-TRUNC");

        when(context.orderRepository.findById(readyOrder.getId())).thenReturn(Optional.of(readyOrder));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-TRUNC"))
                .thenReturn(allOrders);
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-TRUNC"))
                .thenReturn(Optional.of(truck));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);

        assertThatThrownBy(() -> context.service.advanceOrderWithNextStatus(
                readyOrder.getId(), request, warehouseEmployee(5L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10 order(s)")
                .hasMessageContaining("and 5 more");
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

    private static Order createOrder(String orderNumber) {
        return createOrderInCity(orderNumber, "Bielefeld", "33602");
    }

    private static Order createOrderInCity(String orderNumber, String city, String postalCode) {
        WarehouseLocation warehouse = createWarehouse(10L, "WH-MAIN");

        Order order = new Order();
        order.setId(System.nanoTime());
        order.setOrderNumber(orderNumber);
        order.setCustomerName("Test Customer");
        order.setCustomerEmail("test@example.com");
        order.setDeliveryStreet("Main Street 1");
        order.setDeliveryCity(city);
        order.setDeliveryPostalCode(postalCode);
        order.setDeliveryCountry("Germany");
        order.setCreatedAt(Instant.now());
        order.setStatus(OrderStatus.CONFIRMED);
        order.setFulfillmentWarehouse(warehouse);
        order.setItems(List.of());
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


