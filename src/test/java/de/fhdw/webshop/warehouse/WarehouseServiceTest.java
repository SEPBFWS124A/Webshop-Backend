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
import de.fhdw.webshop.warehouse.dto.CompletePackingResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseOrderResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarehouseServiceTest {

    // -------------------------------------------------------------------------
    // complete-packing tests
    // -------------------------------------------------------------------------

    @Test
    void cannotCompleteIfNotAllItemsPicked() {
        TestContext context = newContext();
        Order order = confirmedOrder(false, 5);
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> context.service.completePacking(order.getId(), warehouseEmployee(15L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be fully picked");

        verify(context.orderRepository, never()).save(any(Order.class));
        verify(context.eventPublisher, never()).publishEvent(any());
    }

    @Test
    void completeSetsStatusAndMetadata() {
        TestContext context = newContext();
        Order order = confirmedOrder(true, 5);
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(context.orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.warehouseProductStockRepository.findByProductIdAndWarehouseLocationId(100L, 10L))
                .thenReturn(Optional.of(stock(order.getItems().get(0).getProduct(), order.getFulfillmentWarehouse(), 10)));

        CompletePackingResponse response = context.service.completePacking(order.getId(), warehouseEmployee(15L));

        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.newStatus()).isEqualTo(OrderStatus.PACKED_IN_WAREHOUSE);
        assertThat(response.routePlanningTriggered()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PACKED_IN_WAREHOUSE);
        assertThat(order.getPackedAt()).isNotNull();
        assertThat(order.getPackedByUserId()).isEqualTo("15");
        verify(context.auditLogService).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void autoTriggerCalledOnceAfterCompletePacking() {
        TestContext context = newContext();
        Order order = confirmedOrder(true, 5);
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(context.orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.warehouseProductStockRepository.findByProductIdAndWarehouseLocationId(100L, 10L))
                .thenReturn(Optional.of(stock(order.getItems().get(0).getProduct(), order.getFulfillmentWarehouse(), 10)));

        context.service.completePacking(order.getId(), warehouseEmployee(15L));

        verify(context.eventPublisher, times(1)).publishEvent(any(WarehouseOrderPackedEvent.class));
    }

    @Test
    void completePackingHandlesStockConflict() {
        TestContext context = newContext();
        Order order = confirmedOrder(true, 5);
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(context.warehouseProductStockRepository.findByProductIdAndWarehouseLocationId(100L, 10L))
                .thenReturn(Optional.of(stock(order.getItems().get(0).getProduct(), order.getFulfillmentWarehouse(), 2)));

        assertThatThrownBy(() -> context.service.completePacking(order.getId(), warehouseEmployee(15L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nicht ausreichend Bestand");

        verify(context.orderRepository, never()).save(any(Order.class));
    }

    // -------------------------------------------------------------------------
    // PUT /advance — PACKED_IN_WAREHOUSE -> IN_TRUCK
    // -------------------------------------------------------------------------

    @Test
    void advancePackedToInTruck_success() {
        TestContext context = newContext();
        Order order = orderWithStatus(OrderStatus.PACKED_IN_WAREHOUSE);
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-001"))
                .thenReturn(Optional.of(truck("LKW-001", order.getFulfillmentWarehouse(), TruckStatus.AVAILABLE)));
        when(context.orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.IN_TRUCK, "LKW-001", null, null, null);
        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(order.getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.order().status()).isEqualTo(OrderStatus.IN_TRUCK);
        assertThat(response.order().truckIdentifier()).isEqualTo("LKW-001");
        assertThat(order.getTruckAssignedAt()).isNotNull();
        verify(context.auditLogService).record(any(), eq("TRUCK_ASSIGNED"), any(), any(), any(), any());
    }

    @Test
    void advancePackedToInTruck_missingTruckIdentifier_throws400() {
        TestContext context = newContext();
        Order order = orderWithStatus(OrderStatus.PACKED_IN_WAREHOUSE);
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.IN_TRUCK, null, null, null, null);

        assertThatThrownBy(() -> context.service.advanceOrderWithNextStatus(order.getId(), request, warehouseEmployee(5L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("truckIdentifier is required");
    }

    @Test
    void advancePackedToInTruck_truckAlreadyShipped_throws409() {
        TestContext context = newContext();
        Order order = orderWithStatus(OrderStatus.PACKED_IN_WAREHOUSE);
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-GONE"))
                .thenReturn(Optional.of(truck("LKW-GONE", order.getFulfillmentWarehouse(), TruckStatus.DEPARTED)));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.IN_TRUCK, "LKW-GONE", null, null, null);

        assertThatThrownBy(() -> context.service.advanceOrderWithNextStatus(order.getId(), request, warehouseEmployee(5L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already departed");
    }

    // -------------------------------------------------------------------------
    // PUT /advance — IN_TRUCK -> SHIPPED (batch)
    // -------------------------------------------------------------------------

    @Test
    void advanceInTruckToShipped_batchesAllTruckOrders() {
        TestContext context = newContext();
        Order order1 = orderWithStatus(OrderStatus.IN_TRUCK);
        order1.setTruckIdentifier("LKW-BATCH");
        Order order2 = orderWithStatus(OrderStatus.IN_TRUCK);
        order2.setId(902L);
        order2.setTruckIdentifier("LKW-BATCH");

        when(context.orderRepository.findById(order1.getId())).thenReturn(Optional.of(order1));
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-BATCH", OrderStatus.IN_TRUCK))
                .thenReturn(List.of(order1, order2));
        when(context.orderRepository.findByTruckIdentifierOrderByCreatedAtAsc("LKW-BATCH"))
                .thenReturn(List.of(order1, order2));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-BATCH"))
                .thenReturn(Optional.of(truck("LKW-BATCH", order1.getFulfillmentWarehouse(), TruckStatus.LOADED)));
        when(context.orderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.SHIPPED, null, null, null, null);
        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(order1.getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.batchUpdatedCount()).isEqualTo(2);
        assertThat(order1.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(context.auditLogService).record(any(), eq("TRUCK_DEPARTED"), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // PUT /advance — SHIPPED -> DELIVERED
    // -------------------------------------------------------------------------

    @Test
    void advanceShippedToDelivered_storesCoordinates() {
        TestContext context = newContext();
        Order order = orderWithStatus(OrderStatus.SHIPPED);
        order.setTruckIdentifier("LKW-001");
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(context.orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(context.orderRepository.findByTruckIdentifierAndStatus("LKW-001", OrderStatus.SHIPPED))
                .thenReturn(List.of());
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-001"))
                .thenReturn(Optional.of(truck("LKW-001", order.getFulfillmentWarehouse(), TruckStatus.DEPARTED)));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.DELIVERED, null, null, 50.9375, 6.9603);
        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(order.getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.order().status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveryLatitude()).isEqualTo(50.9375);
        assertThat(order.getDeliveryLongitude()).isEqualTo(6.9603);
        assertThat(order.getDeliveredAt()).isNotNull();
        verify(context.auditLogService).record(any(), eq("ORDER_DELIVERED"), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // PUT /advance — invalid transitions / guard checks
    // -------------------------------------------------------------------------

    @Test
    void advanceInvalidTransition_throws400() {
        TestContext context = newContext();
        Order order = orderWithStatus(OrderStatus.PACKED_IN_WAREHOUSE);
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        // Illegal: PACKED_IN_WAREHOUSE -> DELIVERED (skipping IN_TRUCK and SHIPPED)
        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.DELIVERED, null, null, null, null);

        assertThatThrownBy(() -> context.service.advanceOrderWithNextStatus(order.getId(), request, warehouseEmployee(5L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void advanceFromConfirmedStatusToPackedInWarehouse_isAllowed() {
        TestContext context = newContext();
        Order order = confirmedOrder(true, 5);
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(context.warehouseLocationRepository.findById(10L)).thenReturn(Optional.of(order.getFulfillmentWarehouse()));
        when(context.orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.warehouseProductStockRepository.findByProductIdAndWarehouseLocationId(100L, 10L))
                .thenReturn(Optional.of(stock(order.getItems().get(0).getProduct(), order.getFulfillmentWarehouse(), 10)));

        AdvanceWarehouseOrderRequest request = new AdvanceWarehouseOrderRequest(
                OrderStatus.PACKED_IN_WAREHOUSE, null, 10L, null, null);

        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(order.getId(), request, warehouseEmployee(5L));

        assertThat(response.success()).isTrue();
        assertThat(response.order().status()).isEqualTo(OrderStatus.PACKED_IN_WAREHOUSE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PACKED_IN_WAREHOUSE);
    }

    @Test
    void advanceMissingNextStatus_throws400() {
        TestContext context = newContext();
        Order order = orderWithStatus(OrderStatus.PACKED_IN_WAREHOUSE);
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> context.service.advanceOrderWithNextStatus(order.getId(), null, warehouseEmployee(5L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nextStatus");
    }

    @Test
    void advanceMissingNextStatusOnConfirmed_completesPackingLegacyCompatible() {
        TestContext context = newContext();
        Order order = confirmedOrder(true, 5);
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(context.orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.warehouseProductStockRepository.findByProductIdAndWarehouseLocationId(100L, 10L))
                .thenReturn(Optional.of(stock(order.getItems().get(0).getProduct(), order.getFulfillmentWarehouse(), 10)));

        AdvanceOrderResponse response = context.service.advanceOrderWithNextStatus(order.getId(), null, warehouseEmployee(15L));

        assertThat(response.success()).isTrue();
        assertThat(response.order().status()).isEqualTo(OrderStatus.PACKED_IN_WAREHOUSE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PACKED_IN_WAREHOUSE);
    }

    @Test
    void removeTruckIdentifierClearsRouteAssignment() {
        TestContext context = newContext();
        Order order = orderWithStatus(OrderStatus.PACKED_IN_WAREHOUSE);
        order.setTruckIdentifier("LKW-REMOVE");
        order.setTruckAssignedAt(Instant.now());
        order.setRouteOptimizationId("route-123");
        when(context.orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(context.orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(context.warehouseTruckRepository.findByTruckIdentifier("LKW-REMOVE"))
                .thenReturn(Optional.of(truck("LKW-REMOVE", order.getFulfillmentWarehouse(), TruckStatus.LOADED)));
        when(context.warehouseTruckRepository.save(any(WarehouseTruck.class))).thenAnswer(inv -> inv.getArgument(0));

        WarehouseOrderResponse response = context.service.removeTruckIdentifier(order.getId(), warehouseEmployee(5L));

        assertThat(response.truckIdentifier()).isNull();
        assertThat(order.getTruckIdentifier()).isNull();
        assertThat(order.getTruckAssignedAt()).isNull();
        assertThat(order.getRouteOptimizationId()).isNull();
        verify(context.auditLogService).record(any(), eq("TRUCK_ASSIGNMENT_REMOVED"), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Helpers
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

        WarehouseService service = new WarehouseService(
                orderRepository,
                deliveryAddressRepository,
                addressLookupService,
                productRepository,
                warehouseLocationRepository,
                warehouseProductStockRepository,
                warehouseTruckRepository,
                auditLogService,
                eventPublisher
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

    private static User warehouseEmployee(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("warehouse.employee");
        user.setEmail("warehouse@example.test");
        return user;
    }

    private static Order confirmedOrder(boolean picked, int pickedQuantity) {
        Product product = new Product();
        product.setId(100L);
        product.setName("Monitor");
        product.setRecommendedRetailPrice(new BigDecimal("200.00"));
        product.setStock(50);
        product.setWarehousePosition("A-01-01");

        WarehouseLocation location = new WarehouseLocation();
        location.setId(10L);
        location.setCode("WH-MAIN");
        location.setName("Main Warehouse");
        location.setStreet("Main Street 1");
        location.setPostalCode("33602");
        location.setCity("Bielefeld");
        location.setCountry("Germany");
        location.setMainLocation(true);
        location.setActive(true);

        Order order = new Order();
        order.setId(900L);
        order.setOrderNumber("ORD-900");
        order.setCustomerName("Max Mustermann");
        order.setCustomerEmail("max@example.test");
        order.setDeliveryStreet("Main Street 5");
        order.setDeliveryCity("Bielefeld");
        order.setDeliveryPostalCode("33602");
        order.setDeliveryCountry("Germany");
        order.setCreatedAt(Instant.now());
        order.setStatus(OrderStatus.CONFIRMED);
        order.setFulfillmentWarehouse(location);

        OrderItem item = new OrderItem();
        item.setId(901L);
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(5);
        item.setPriceAtOrderTime(new BigDecimal("200.00"));
        if (picked) {
            item.setPickedAt(Instant.now());
            item.setPickedByUserId("15");
            item.setPickedQuantity(pickedQuantity);
        }
        order.setItems(List.of(item));
        return order;
    }

    /** Creates a minimal order in the given status (no items needed for advance tests). */
    private static Order orderWithStatus(OrderStatus status) {
        WarehouseLocation location = new WarehouseLocation();
        location.setId(10L);
        location.setCode("WH-MAIN");
        location.setName("Main Warehouse");
        location.setStreet("Main Street 1");
        location.setPostalCode("33602");
        location.setCity("Bielefeld");
        location.setCountry("Germany");
        location.setMainLocation(true);
        location.setActive(true);

        Order order = new Order();
        order.setId(900L);
        order.setOrderNumber("ORD-900");
        order.setCustomerName("Max Mustermann");
        order.setCustomerEmail("max@example.test");
        order.setDeliveryStreet("Main Street 5");
        order.setDeliveryCity("Bielefeld");
        order.setDeliveryPostalCode("33602");
        order.setDeliveryCountry("Germany");
        order.setCreatedAt(Instant.now());
        order.setStatus(status);
        order.setFulfillmentWarehouse(location);
        order.setItems(List.of());
        return order;
    }

    private static WarehouseProductStock stock(Product product, WarehouseLocation location, int quantity) {
        WarehouseProductStock stock = new WarehouseProductStock();
        stock.setProduct(product);
        stock.setWarehouseLocation(location);
        stock.setQuantity(quantity);
        return stock;
    }

    private static WarehouseTruck truck(String identifier, WarehouseLocation location, TruckStatus status) {
        WarehouseTruck truck = new WarehouseTruck();
        truck.setTruckIdentifier(identifier);
        truck.setOriginWarehouseLocation(location);
        truck.setCurrentWarehouseLocation(status == TruckStatus.DEPARTED ? null : location);
        truck.setStatus(status);
        truck.setCapacityOrders(10);
        return truck;
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

