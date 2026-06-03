package de.fhdw.webshop.warehouse;

import de.fhdw.webshop.address.AddressLookupService;
import de.fhdw.webshop.address.GeocodedAddressResponse;
import de.fhdw.webshop.address.RoadRouteRequest;
import de.fhdw.webshop.address.RoadRouteResponse;
import de.fhdw.webshop.address.RoadRouteStopRequest;
import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.user.DeliveryAddress;
import de.fhdw.webshop.user.DeliveryAddressRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.warehouse.dto.AdvanceOrderResponse;
import de.fhdw.webshop.warehouse.dto.AdvanceWarehouseOrderRequest;
import de.fhdw.webshop.warehouse.dto.AutoAssignTruckIdentifiersResponse;
import de.fhdw.webshop.warehouse.dto.DepartureReadinessResponse;
import de.fhdw.webshop.warehouse.dto.StartDepartureResponse;
import de.fhdw.webshop.warehouse.dto.AutoAssignTruckRouteResponse;
import de.fhdw.webshop.warehouse.dto.CompletePackingResponse;
import de.fhdw.webshop.warehouse.dto.PickOrderItemRequest;
import de.fhdw.webshop.warehouse.dto.TruckAssignmentChangeResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseTruckResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseLocationResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseOrderItemResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseOrderResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseTransferRequest;
import de.fhdw.webshop.warehouse.dto.WarehouseTransferResponse;
import jakarta.persistence.EntityNotFoundException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private static final List<OrderStatus> ACTIVE_WAREHOUSE_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PACKED_IN_WAREHOUSE,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.IN_TRUCK,
            OrderStatus.SHIPPED
    );

    private static final List<OrderStatus> TRUCK_ASSIGNABLE_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PACKED_IN_WAREHOUSE,
            OrderStatus.IN_TRUCK
    );

    private static final List<OrderStatus> WAREHOUSE_REASSIGNABLE_STATUSES = List.of(
            OrderStatus.PENDING,
            OrderStatus.CONFIRMED
    );

    private static final List<OrderStatus> TRUCK_ROUTE_VISIBLE_STATUSES = List.of(
            OrderStatus.PACKED_IN_WAREHOUSE,
            OrderStatus.IN_TRUCK,
            OrderStatus.SHIPPED
    );

    /** Statuses considered "active" for departure readiness checks (excludes completed/cancelled). */
    private static final List<OrderStatus> TRUCK_DEPARTURE_RELEVANT_STATUSES = List.of(
            OrderStatus.PACKED_IN_WAREHOUSE,
            OrderStatus.IN_TRUCK
    );

    private static final List<OrderStatus> TRUCK_ASSIGNED_COUNT_STATUSES = List.of(
            OrderStatus.PACKED_IN_WAREHOUSE,
            OrderStatus.IN_TRUCK,
            OrderStatus.SHIPPED
    );

    /** Statuses accepted as *input* by PUT /advance. */
    private static final Set<OrderStatus> ADVANCE_ENDPOINT_ALLOWED_STATUSES = Set.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PACKED_IN_WAREHOUSE,
            OrderStatus.IN_TRUCK,
            OrderStatus.SHIPPED
    );
    private static final double MAX_TRUCK_CLUSTER_DISTANCE_KM = 120.0;

    private final OrderRepository orderRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final AddressLookupService addressLookupService;
    private final ProductRepository productRepository;
    private final WarehouseLocationRepository warehouseLocationRepository;
    private final WarehouseProductStockRepository warehouseProductStockRepository;
    private final WarehouseTruckRepository warehouseTruckRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final WarehouseStockBalanceService warehouseStockBalanceService;
    private final de.fhdw.webshop.subscription.SubscriptionService subscriptionService;

    @Transactional
    public List<WarehouseOrderResponse> listOrders(OrderStatus status) {
        return listOrders(status == null ? null : List.of(status), null);
    }

    @Transactional
    public List<WarehouseOrderResponse> listOrders(OrderStatus status, Long warehouseLocationId) {
        return listOrders(status == null ? null : List.of(status), warehouseLocationId);
    }

    /**
     * Primary implementation: supports filtering by one or more statuses.
     * Accepts comma-separated values forwarded from the controller as a parsed list.
     */
    @Transactional
    public List<WarehouseOrderResponse> listOrders(List<OrderStatus> statuses, Long warehouseLocationId) {
        List<Order> orders;
        if (statuses == null || statuses.isEmpty()) {
            orders = orderRepository.findByStatusNotInOrderByCreatedAtAsc(List.of(
                    OrderStatus.DELIVERED,
                    OrderStatus.CANCELLED,
                    OrderStatus.Pending_Approval,
                    OrderStatus.Rejected));
        } else if (statuses.size() == 1) {
            orders = orderRepository.findByStatusOrderByCreatedAtAsc(statuses.get(0));
        } else {
            orders = orderRepository.findByStatusInOrderByCreatedAtAsc(statuses);
        }

        boolean useStatusFilter = statuses == null || statuses.isEmpty();
        List<Order> filteredOrders = orders.stream()
                .filter(order -> !useStatusFilter || ACTIVE_WAREHOUSE_STATUSES.contains(order.getStatus()))
                .map(order -> {
                    ensureFulfillmentWarehouse(order);
                    return order;
                })
                .filter(order -> warehouseLocationId == null
                        || (order.getFulfillmentWarehouse() != null
                        && warehouseLocationId.equals(order.getFulfillmentWarehouse().getId())))
                .toList();

        Map<String, String> regionLabels = buildRegionLabels(filteredOrders);
        Map<String, String> suggestedTruckIdentifiers = buildSuggestedTruckIdentifiers(filteredOrders);

        return filteredOrders.stream()
                .map(order -> toResponse(
                        order,
                        resolveRegionKey(order),
                        regionLabels,
                        suggestedTruckIdentifiers
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WarehouseLocationResponse> listLocations() {
        return warehouseLocationRepository.findActiveLocations().stream()
                .map(this::toLocationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WarehouseTruckResponse> listTrucks(Long warehouseLocationId) {
        if (warehouseLocationId != null) {
            resolveWarehouseLocation(warehouseLocationId);
        }
        List<WarehouseTruck> trucks = warehouseTruckRepository.findFleetByWarehouseLocation(warehouseLocationId);

        return trucks.stream().map(this::toTruckResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WarehouseOrderResponse> listOrdersForTruck(String truckIdentifier) {
        return orderRepository.findByTruckIdentifierAndStatusInOrderByCreatedAtAsc(
                        truckIdentifier,
                        TRUCK_ROUTE_VISIBLE_STATUSES
                ).stream()
                .map(order -> toResponse(
                        order,
                        resolveRegionKey(order),
                        Map.of(resolveRegionKey(order), buildRegionLabel(resolveRegionKey(order), List.of(order))),
                        Map.of(resolveRegionKey(order), normalizeTruckIdentifier(order.getTruckIdentifier(), createSuggestedTruckIdentifier(order)))
                ))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Advance order — driver/logistics flow
    // -------------------------------------------------------------------------

    /**
     * Handles the driver/logistics status transitions for the PUT /advance endpoint.
     * Accepts orders in one of: CONFIRMED, PACKED_IN_WAREHOUSE, IN_TRUCK, SHIPPED.
     * The {@code nextStatus} field in the request is mandatory.
     *
     * <ul>
     *   <li>CONFIRMED → PACKED_IN_WAREHOUSE: completes warehouse packing and optional stock booking.</li>
     *   <li>PACKED_IN_WAREHOUSE → IN_TRUCK: requires {@code truckIdentifier}; truck must not have SHIPPED orders.</li>
     *   <li>IN_TRUCK → SHIPPED: batch-advances all orders on the same truck.</li>
     *   <li>SHIPPED → DELIVERED: stores optional delivery coordinates.</li>
     * </ul>
     */
    @Transactional
    public AdvanceOrderResponse advanceOrderWithNextStatus(
            Long orderId,
            AdvanceWarehouseOrderRequest request,
            User currentUser
    ) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        // Backward compatibility for legacy clients that used PUT /advance to complete packing.
        if (request == null || request.nextStatus() == null) {
            if (order.getStatus() == OrderStatus.CONFIRMED) {
                completePackingInternal(order, null, currentUser);
                return new AdvanceOrderResponse(
                        true,
                        new AdvanceOrderResponse.OrderSummary(
                                order.getId(),
                                order.getStatus(),
                                order.getTruckIdentifier(),
                                order.getUpdatedAt()
                        ),
                        "Packing completed successfully",
                        1
                );
            }
            throw new IllegalArgumentException("Field 'nextStatus' is required. For package completion use POST /api/warehouse/orders/{orderId}/complete-packing");
        }

        OrderStatus requestedNextStatus = request.nextStatus();
        if (requestedNextStatus == order.getStatus()) {
            // Some clients send the current status instead of the target status.
            // We normalize this to the next valid workflow status.
            requestedNextStatus = switch (order.getStatus()) {
                case CONFIRMED -> OrderStatus.PACKED_IN_WAREHOUSE;
                case PACKED_IN_WAREHOUSE -> OrderStatus.IN_TRUCK;
                case IN_TRUCK -> OrderStatus.SHIPPED;
                case SHIPPED -> OrderStatus.DELIVERED;
                default -> requestedNextStatus;
            };
        }

        if (!ADVANCE_ENDPOINT_ALLOWED_STATUSES.contains(order.getStatus())) {
            throw new IllegalArgumentException(
                    "Order " + orderId + " has status " + order.getStatus()
                            + " which cannot be advanced via this endpoint. "
                            + "Allowed input statuses: CONFIRMED, PACKED_IN_WAREHOUSE, IN_TRUCK, SHIPPED");
        }

        if (!isAllowedTransition(order.getStatus(), requestedNextStatus)) {
            throw new IllegalArgumentException(
                    "Status transition " + order.getStatus() + " → " + requestedNextStatus
                            + " is not allowed. "
                            + "Valid transitions: PACKED_IN_WAREHOUSE→IN_TRUCK, IN_TRUCK→SHIPPED, SHIPPED→DELIVERED");
        }

        return switch (order.getStatus()) {
            case CONFIRMED -> advanceConfirmedToPacked(order, request, currentUser);
            case PACKED_IN_WAREHOUSE -> advanceToInTruck(order, request, currentUser);
            case IN_TRUCK -> advanceToShipped(order, request, currentUser);
            case SHIPPED -> advanceToDelivered(order, request, currentUser);
            default -> throw new IllegalArgumentException("Unexpected status: " + order.getStatus());
        };
    }

    private AdvanceOrderResponse advanceConfirmedToPacked(Order order, AdvanceWarehouseOrderRequest request, User currentUser) {
        completePackingInternal(order, request.warehouseLocationId(), currentUser);
        return new AdvanceOrderResponse(
                true,
                new AdvanceOrderResponse.OrderSummary(order.getId(), order.getStatus(), order.getTruckIdentifier(), order.getUpdatedAt()),
                "Order packed in warehouse",
                1
        );
    }

    private AdvanceOrderResponse advanceToInTruck(Order order, AdvanceWarehouseOrderRequest request, User currentUser) {
        String truckIdentifier = normalizeTruckIdentifier(request.truckIdentifier(), order.getTruckIdentifier());
        if (truckIdentifier == null) {
            throw new IllegalArgumentException(
                    "truckIdentifier is required when moving an order to IN_TRUCK");
        }

        WarehouseTruck truck = resolveTruck(truckIdentifier);
        if (truck.getDepartureTime() != null || truck.getStatus() == TruckStatus.DEPARTED) {
            throw new IllegalStateException(
                    "Truck '" + truckIdentifier + "' has already departed and cannot accept new packages");
        }

        WarehouseLocation fulfillmentWarehouse = ensureFulfillmentWarehouse(order);
        ensureTruckIsAtWarehouse(truck, fulfillmentWarehouse);
        ensureTruckIsAssignable(truck);

        order.setTruckIdentifier(truckIdentifier);
        order.setTruckAssignedAt(Instant.now());
        order.setRouteOptimizationId(truck.getRouteOptimizationId() != null
                ? truck.getRouteOptimizationId()
                : java.util.UUID.randomUUID().toString());
        order.setStatus(OrderStatus.IN_TRUCK);
        Order saved = orderRepository.save(order);

        truck.setCurrentWarehouseLocation(fulfillmentWarehouse);
        truck.setOriginWarehouseLocation(fulfillmentWarehouse);
        truck.setStatus(TruckStatus.LOADED);
        warehouseTruckRepository.save(truck);

        auditLogService.record(
                currentUser, "TRUCK_ASSIGNED", "Order", saved.getId(),
                currentUser == null ? AuditInitiator.SYSTEM : AuditInitiator.USER,
                "Order " + saved.getId() + " loaded into truck " + truckIdentifier);

        return new AdvanceOrderResponse(
                true,
                new AdvanceOrderResponse.OrderSummary(saved.getId(), saved.getStatus(), saved.getTruckIdentifier(), saved.getUpdatedAt()),
                "Order successfully loaded into truck " + truckIdentifier,
                1);
    }

    private AdvanceOrderResponse advanceToShipped(Order order, AdvanceWarehouseOrderRequest request, User currentUser) {
        String truckIdentifier = order.getTruckIdentifier();
        if (truckIdentifier == null) {
            throw new IllegalStateException("Order " + order.getId() + " has no truck identifier assigned");
        }

        WarehouseTruck truck = resolveTruck(truckIdentifier);
        ensureTruckReadyForDeparture(truckIdentifier, truck);

        // Batch: advance all IN_TRUCK orders on the same truck to SHIPPED
        List<Order> truckOrders = orderRepository.findByTruckIdentifierAndStatus(truckIdentifier, OrderStatus.IN_TRUCK);
        if (truckOrders.isEmpty()) {
            truckOrders = List.of(order);
        }
        truckOrders.forEach(o -> o.setStatus(OrderStatus.SHIPPED));
        orderRepository.saveAll(truckOrders);

        truck.setDepartureTime(Instant.now());
        truck.setCurrentWarehouseLocation(null);
        truck.setStatus(TruckStatus.DEPARTED);
        truck.setCompletedAt(null);
        warehouseTruckRepository.save(truck);

        int batchCount = truckOrders.size();
        auditLogService.record(
                currentUser, "TRUCK_DEPARTED", "Order", order.getId(),
                currentUser == null ? AuditInitiator.SYSTEM : AuditInitiator.USER,
                "Truck " + truckIdentifier + " departed with " + batchCount + " package(s)");

        Order saved = truckOrders.stream()
                .filter(o -> o.getId().equals(order.getId()))
                .findFirst()
                .orElse(order);
        return new AdvanceOrderResponse(
                true,
                new AdvanceOrderResponse.OrderSummary(saved.getId(), saved.getStatus(), saved.getTruckIdentifier(), saved.getUpdatedAt()),
                "Truck " + truckIdentifier + " departed — " + batchCount + " order(s) are now SHIPPED",
                batchCount);
    }

    private AdvanceOrderResponse advanceToDelivered(Order order, AdvanceWarehouseOrderRequest request, User currentUser) {
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(Instant.now());
        if (request.latitude() != null) {
            order.setDeliveryLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            order.setDeliveryLongitude(request.longitude());
        }
        Order saved = orderRepository.save(order);

        // Handle stock booking for internal transfers
        warehouseStockBalanceService.handleInternalTransferDelivered(saved);

        String coordInfo = request.latitude() != null
                ? " at coordinates " + request.latitude() + "," + request.longitude()
                : "";
        auditLogService.record(
                currentUser, "ORDER_DELIVERED", "Order", saved.getId(),
                currentUser == null ? AuditInitiator.SYSTEM : AuditInitiator.USER,
                "Package delivered to customer" + coordInfo);

        // Log tour completion when all orders for this truck are now delivered
        if (saved.getTruckIdentifier() != null) {
            boolean tourComplete = orderRepository
                    .findByTruckIdentifierAndStatus(saved.getTruckIdentifier(), OrderStatus.SHIPPED)
                    .isEmpty();
            if (tourComplete) {
                WarehouseTruck truck = warehouseTruckRepository.findByTruckIdentifier(saved.getTruckIdentifier()).orElse(null);
                if (truck != null) {
                    truck.setStatus(TruckStatus.AVAILABLE);
                    truck.setCompletedAt(Instant.now());
                    truck.setDepartureTime(null);
                    truck.setCurrentWarehouseLocation(truck.getOriginWarehouseLocation());
                    warehouseTruckRepository.save(truck);
                }
                auditLogService.record(
                        currentUser, "TRUCK_TOUR_COMPLETED", "Order", saved.getId(),
                        currentUser == null ? AuditInitiator.SYSTEM : AuditInitiator.USER,
                        "All orders for truck " + saved.getTruckIdentifier() + " have been delivered");
            }
        }

        return new AdvanceOrderResponse(
                true,
                new AdvanceOrderResponse.OrderSummary(saved.getId(), saved.getStatus(), saved.getTruckIdentifier(), saved.getUpdatedAt()),
                "Package successfully delivered to customer",
                1);
    }

    /** Validates whether a status transition is permitted via the advance endpoint. */
    private static boolean isAllowedTransition(OrderStatus from, OrderStatus to) {
        return (from == OrderStatus.CONFIRMED && to == OrderStatus.PACKED_IN_WAREHOUSE)
                || (from == OrderStatus.PACKED_IN_WAREHOUSE && to == OrderStatus.IN_TRUCK)
                || (from == OrderStatus.IN_TRUCK && to == OrderStatus.SHIPPED)
                || (from == OrderStatus.SHIPPED && to == OrderStatus.DELIVERED);
    }

    // -------------------------------------------------------------------------
    // Legacy advance — kept for internal use by warehouse/truck sub-endpoints
    // -------------------------------------------------------------------------

    @Transactional
    public WarehouseOrderResponse advanceOrder(Long orderId, String requestedTruckIdentifier) {
        return advanceOrder(orderId, requestedTruckIdentifier, null, null);
    }

    @Transactional
    public WarehouseOrderResponse advanceOrder(Long orderId, String requestedTruckIdentifier, Long warehouseLocationId) {
        return advanceOrder(orderId, requestedTruckIdentifier, warehouseLocationId, null);
    }

    @Transactional
    public WarehouseOrderResponse advanceOrder(
            Long orderId,
            String requestedTruckIdentifier,
            Long warehouseLocationId,
            User currentUser
    ) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        OrderStatus nextStatus = determineNextStatus(order);
        if (nextStatus == null) {
            throw new IllegalArgumentException("Order status can no longer be advanced");
        }

        if (nextStatus == OrderStatus.IN_TRUCK) {
            String resolvedTruckIdentifier = normalizeTruckIdentifier(
                    requestedTruckIdentifier,
                    order.getTruckIdentifier()
            );
            if (resolvedTruckIdentifier == null) {
                throw new IllegalArgumentException("A truck identifier is required before the order can be moved into the truck");
            }
            order.setTruckIdentifier(resolvedTruckIdentifier);
        } else if (isClickAndCollectOrder(order)) {
            order.setTruckIdentifier(null);
        }

        if (nextStatus == OrderStatus.PACKED_IN_WAREHOUSE) {
            completePackingInternal(order, warehouseLocationId, currentUser);
            String regionKey = resolveRegionKey(order);
            return toResponse(
                    order,
                    regionKey,
                    Map.of(regionKey, buildRegionLabel(regionKey, List.of(order))),
                    Map.of(regionKey, normalizeTruckIdentifier(order.getTruckIdentifier(), createSuggestedTruckIdentifier(order)))
            );
        }

        WarehouseLocation fulfillmentWarehouse = resolveWarehouseForOrder(order, warehouseLocationId);
        order.setFulfillmentWarehouse(fulfillmentWarehouse);

        order.setStatus(nextStatus);
        if (nextStatus == OrderStatus.DELIVERED && order.getDeliveredAt() == null) {
            order.setDeliveredAt(Instant.now());
        }
        Order savedOrder = orderRepository.save(order);
        String regionKey = resolveRegionKey(savedOrder);
        return toResponse(
                savedOrder,
                regionKey,
                Map.of(regionKey, buildRegionLabel(regionKey, List.of(savedOrder))),
                Map.of(regionKey, normalizeTruckIdentifier(savedOrder.getTruckIdentifier(), createSuggestedTruckIdentifier(savedOrder)))
        );
    }

    @Transactional
    public WarehouseOrderResponse updateItemPickState(
            Long orderId,
            Long itemId,
            PickOrderItemRequest request,
            User currentUser
    ) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        ensureStatus(order, OrderStatus.CONFIRMED, "Pick updates are only allowed for confirmed orders");

        OrderItem orderItem = findOrderItem(order, itemId);
        WarehouseLocation fulfillmentWarehouse = ensureFulfillmentWarehouse(order);
        int requestedPickQuantity = resolvePickedQuantity(request, orderItem);

        if (request.picked()) {
            Map<Long, Integer> stockByProductId = resolveWarehouseStockByProductId(order, fulfillmentWarehouse);
            int stock = stockByProductId.getOrDefault(orderItem.getProduct().getId(), 0);
            if (stock < requestedPickQuantity) {
                throw new IllegalStateException("Not enough stock in fulfillment warehouse for item " + orderItem.getProduct().getName());
            }
            if (order.getPackingStartedAt() == null) {
                order.setPackingStartedAt(Instant.now());
            }
            orderItem.setPickedAt(Instant.now());
            orderItem.setPickedByUserId(resolveUserIdentifier(currentUser));
            orderItem.setPickedQuantity(requestedPickQuantity);
        } else {
            orderItem.setPickedAt(null);
            orderItem.setPickedByUserId(null);
            orderItem.setPickedQuantity(null);
        }

        Order savedOrder = orderRepository.save(order);
        String regionKey = resolveRegionKey(savedOrder);
        return toResponse(
                savedOrder,
                regionKey,
                Map.of(regionKey, buildRegionLabel(regionKey, List.of(savedOrder))),
                Map.of(regionKey, normalizeTruckIdentifier(savedOrder.getTruckIdentifier(), createSuggestedTruckIdentifier(savedOrder)))
        );
    }

    @Transactional
    public CompletePackingResponse completePacking(Long orderId, User currentUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        completePackingInternal(order, null, currentUser);
        String assignedTruckIdentifier = isClickAndCollectOrder(order)
                ? null
                : normalizeTruckIdentifier(order.getTruckIdentifier(), createSuggestedTruckIdentifier(order));

        return new CompletePackingResponse(
                order.getId(),
                order.getStatus(),
                assignedTruckIdentifier,
                null,
                true,
                "Packing completed successfully and dispatch planning was triggered"
        );
    }

    @Transactional
    public WarehouseOrderResponse updateFulfillmentWarehouse(Long orderId, Long warehouseLocationId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (!List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED).contains(order.getStatus())) {
            throw new IllegalArgumentException("Das Auslieferungslager kann nur vor dem Packen im Lager geändert werden");
        }

        WarehouseLocation warehouseLocation = resolveWarehouseForOrder(order, warehouseLocationId);
        order.setFulfillmentWarehouse(warehouseLocation);
        Order savedOrder = orderRepository.save(order);
        String regionKey = resolveRegionKey(savedOrder);
        return toResponse(
                savedOrder,
                regionKey,
                Map.of(regionKey, buildRegionLabel(regionKey, List.of(savedOrder))),
                Map.of(regionKey, normalizeTruckIdentifier(savedOrder.getTruckIdentifier(), createSuggestedTruckIdentifier(savedOrder)))
        );
    }

    @Transactional
    public WarehouseOrderResponse updateTruckIdentifier(Long orderId, String requestedTruckIdentifier) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (!TRUCK_ASSIGNABLE_STATUSES.contains(order.getStatus())) {
            throw new IllegalArgumentException("Truck assignments can only be changed before the order is in delivery");
        }
        if (isClickAndCollectOrder(order)) {
            throw new IllegalArgumentException("Für Click & Collect wird keine LKW-ID benötigt");
        }

        String resolvedTruckIdentifier = normalizeTruckIdentifier(requestedTruckIdentifier, null);
        if (resolvedTruckIdentifier == null) {
            throw new IllegalArgumentException("A truck identifier is required");
        }

        WarehouseTruck truck = resolveTruck(resolvedTruckIdentifier);
        WarehouseLocation fulfillmentWarehouse = ensureFulfillmentWarehouse(order);
        ensureTruckIsAtWarehouse(truck, fulfillmentWarehouse);
        ensureTruckIsAssignable(truck);

        order.setTruckIdentifier(resolvedTruckIdentifier);
        order.setTruckAssignedAt(Instant.now());
        order.setRouteOptimizationId(truck.getRouteOptimizationId());
        truck.setCurrentWarehouseLocation(fulfillmentWarehouse);
        truck.setOriginWarehouseLocation(fulfillmentWarehouse);
        truck.setStatus(TruckStatus.LOADED);
        truck.setCompletedAt(null);
        Order savedOrder = orderRepository.save(order);
        warehouseTruckRepository.save(truck);
        String regionKey = resolveRegionKey(savedOrder);
        return toResponse(
                savedOrder,
                regionKey,
                Map.of(regionKey, buildRegionLabel(regionKey, List.of(savedOrder))),
                Map.of(regionKey, normalizeTruckIdentifier(savedOrder.getTruckIdentifier(), createSuggestedTruckIdentifier(savedOrder)))
        );
    }

    @Transactional
    public WarehouseOrderResponse removeTruckIdentifier(Long orderId, User currentUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Truck already departed: assignment cannot be removed for SHIPPED/DELIVERED orders");
        }
        if (order.getStatus() != OrderStatus.PACKED_IN_WAREHOUSE && order.getStatus() != OrderStatus.IN_TRUCK) {
            throw new IllegalStateException("Truck assignment can only be removed while order is PACKED_IN_WAREHOUSE or IN_TRUCK");
        }
        if (order.getTruckIdentifier() == null) {
            return toResponse(
                    order,
                    resolveRegionKey(order),
                    Map.of(resolveRegionKey(order), buildRegionLabel(resolveRegionKey(order), List.of(order))),
                    Map.of(resolveRegionKey(order), createSuggestedTruckIdentifier(order))
            );
        }

        String previousTruckIdentifier = order.getTruckIdentifier();
        if (order.getStatus() == OrderStatus.IN_TRUCK) {
            order.setStatus(OrderStatus.PACKED_IN_WAREHOUSE);
        }
        order.setTruckIdentifier(null);
        order.setTruckAssignedAt(null);
        order.setRouteOptimizationId(null);
        Order savedOrder = orderRepository.save(order);

        WarehouseTruck truck = warehouseTruckRepository.findByTruckIdentifier(previousTruckIdentifier).orElse(null);
        if (truck != null) {
            refreshTruckState(truck);
        }

        auditLogService.record(
                currentUser,
                "TRUCK_ASSIGNMENT_REMOVED",
                "Order",
                savedOrder.getId(),
                currentUser == null ? AuditInitiator.SYSTEM : AuditInitiator.USER,
                "Order " + savedOrder.getId() + " aus Truck " + previousTruckIdentifier
                        + " entfernt und zurueckgesetzt auf PACKED_IN_WAREHOUSE"
        );

        String regionKey = resolveRegionKey(savedOrder);
        return toResponse(
                savedOrder,
                regionKey,
                Map.of(regionKey, buildRegionLabel(regionKey, List.of(savedOrder))),
                Map.of(regionKey, normalizeTruckIdentifier(savedOrder.getTruckIdentifier(), createSuggestedTruckIdentifier(savedOrder)))
        );
    }

    @Transactional
    public AutoAssignTruckIdentifiersResponse autoAssignTruckIdentifiers() {
        List<Order> packedOrders = orderRepository.findByStatusOrderByCreatedAtAsc(OrderStatus.PACKED_IN_WAREHOUSE)
                .stream()
                .filter(order -> !isClickAndCollectOrder(order))
                .map(order -> {
                    ensureFulfillmentWarehouse(order);
                    return order;
                })
                .toList();

        Map<Long, List<Order>> ordersByWarehouseId = packedOrders.stream()
                .collect(Collectors.groupingBy(
                        order -> order.getFulfillmentWarehouse().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<TruckAssignmentChangeResponse> changes = new ArrayList<>();
        List<WarehouseTruck> trucksToSave = new ArrayList<>();
        List<Order> ordersToSave = new ArrayList<>();
        List<AutoAssignTruckRouteResponse> routesCreated = new ArrayList<>();

        ordersByWarehouseId.forEach((warehouseId, warehouseOrders) -> {
            WarehouseLocation warehouse = warehouseOrders.getFirst().getFulfillmentWarehouse();
            List<WarehouseTruck> availableTrucks = new ArrayList<>(warehouseTruckRepository
                    .findByCurrentWarehouseLocationIdOrderByTruckIdentifierAsc(warehouseId));
            Map<String, Integer> provisionalLoadByTruckIdentifier = new LinkedHashMap<>();

            List<TruckAssignmentCandidate> candidates = warehouseOrders.stream()
                    .map(this::toTruckAssignmentCandidate)
                    .sorted(Comparator.comparing(candidate -> candidate.order().getCreatedAt()))
                    .toList();

            List<TruckAssignmentCluster> clusters = new ArrayList<>();
            for (TruckAssignmentCandidate candidate : candidates) {
                TruckAssignmentCluster matchingCluster = clusters.stream()
                        .filter(cluster -> cluster.canInclude(candidate))
                        .min(Comparator.comparingDouble(cluster -> cluster.distanceTo(candidate)))
                        .orElse(null);

                if (matchingCluster == null) {
                    clusters.add(new TruckAssignmentCluster(candidate));
                } else {
                    matchingCluster.add(candidate);
                }
            }

            for (TruckAssignmentCluster cluster : clusters) {
                WarehouseTruck truck = resolveTruckForCluster(cluster, warehouse, availableTrucks, provisionalLoadByTruckIdentifier);
                String regionLabel = cluster.buildClusterLabel();
                String resolvedTruckIdentifier = truck.getTruckIdentifier();
                String routeOptimizationId = truck.getRouteOptimizationId();
                if (routeOptimizationId == null || routeOptimizationId.isBlank()) {
                    routeOptimizationId = java.util.UUID.randomUUID().toString();
                    truck.setRouteOptimizationId(routeOptimizationId);
                }
                truck.setCurrentWarehouseLocation(warehouse);
                truck.setOriginWarehouseLocation(warehouse);
                truck.setStatus(TruckStatus.LOADED);

                for (TruckAssignmentCandidate candidate : cluster.orders()) {
                    Order order = candidate.order();
                    String previousTruckIdentifier = normalizeTruckIdentifier(order.getTruckIdentifier(), null);
                    if (!Objects.equals(previousTruckIdentifier, resolvedTruckIdentifier)) {
                        changes.add(new TruckAssignmentChangeResponse(
                                order.getId(),
                                order.getOrderNumber(),
                                regionLabel,
                                previousTruckIdentifier,
                                resolvedTruckIdentifier
                        ));
                        auditLogService.recordSystemAction(
                                "TRUCK_ASSIGNED",
                                "Order",
                                order.getId(),
                                "Order " + order.getId() + " auto-assigned to truck "
                                        + resolvedTruckIdentifier + " (region: " + regionLabel + ")"
                        );
                    }
                    order.setTruckIdentifier(resolvedTruckIdentifier);
                    if (order.getTruckAssignedAt() == null || !Objects.equals(previousTruckIdentifier, resolvedTruckIdentifier)) {
                        order.setTruckAssignedAt(Instant.now());
                    }
                    order.setRouteOptimizationId(routeOptimizationId);
                    ordersToSave.add(order);
                }

                trucksToSave.add(truck);
                if (!availableTrucks.contains(truck)) {
                    availableTrucks.add(truck);
                }
                provisionalLoadByTruckIdentifier.merge(resolvedTruckIdentifier, cluster.orders().size(), Integer::sum);
                routesCreated.add(buildAutoAssignTruckRouteResponse(truck, warehouse, cluster));
            }
        });

        if (!ordersToSave.isEmpty()) {
            orderRepository.saveAll(ordersToSave);
        }
        if (!trucksToSave.isEmpty()) {
            warehouseTruckRepository.saveAll(trucksToSave);
        }
        List<String> trucksUsed = trucksToSave.stream()
                .map(WarehouseTruck::getTruckIdentifier)
                .distinct()
                .toList();

        return new AutoAssignTruckIdentifiersResponse(
                listOrders(List.of(OrderStatus.PACKED_IN_WAREHOUSE), null),
                changes,
                changes.size(),
                trucksUsed,
                routesCreated
        );
    }

    // -------------------------------------------------------------------------
    // Truck departure endpoints
    // -------------------------------------------------------------------------

    /**
     * Checks departure readiness for a specific truck.
     * Only considers active orders (PACKED_IN_WAREHOUSE, IN_TRUCK).
     * DELIVERED/CANCELLED orders from previous trips are excluded.
     */
    @Transactional(readOnly = true)
    public DepartureReadinessResponse checkDepartureReadiness(String truckIdentifier) {
        WarehouseTruck truck = resolveTruck(truckIdentifier);
        List<Order> allTruckOrders = loadTruckOrders(truckIdentifier);

        // Only consider active orders — not delivered/cancelled from previous trips
        List<Order> activeOrders = allTruckOrders.stream()
                .filter(order -> TRUCK_DEPARTURE_RELEVANT_STATUSES.contains(order.getStatus()))
                .toList();

        List<DepartureReadinessResponse.OrderReadinessDetail> orderDetails = activeOrders.stream()
                .map(order -> new DepartureReadinessResponse.OrderReadinessDetail(
                        order.getId(),
                        order.getOrderNumber(),
                        order.getStatus(),
                        order.getStatus() == OrderStatus.IN_TRUCK,
                        order.getDeliveryCity(),
                        order.getDeliveryPostalCode()
                ))
                .toList();

        int readyCount = (int) activeOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.IN_TRUCK)
                .count();
        int notReadyCount = activeOrders.size() - readyCount;

        List<String> issues = new ArrayList<>();
        if (activeOrders.isEmpty()) {
            issues.add("Keine aktiven Bestellungen auf diesem LKW zugewiesen");
        }
        if (truck.getDepartureTime() != null || truck.getStatus() == TruckStatus.DEPARTED) {
            issues.add("LKW ist bereits abgefahren");
        }
        boolean hasShipped = allTruckOrders.stream()
                .anyMatch(order -> order.getStatus() == OrderStatus.SHIPPED);
        if (hasShipped) {
            issues.add("LKW hat bereits versendete Bestellungen auf der aktuellen Tour");
        }
        activeOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.IN_TRUCK)
                .forEach(o -> issues.add("Bestellung " + o.getOrderNumber() + " ist noch nicht verladen (Status: " + o.getStatus() + ")"));

        boolean readyToDepart = issues.isEmpty() && !activeOrders.isEmpty() && notReadyCount == 0;

        String message = readyToDepart
                ? "LKW " + truckIdentifier + " ist abfahrbereit mit " + readyCount + " Paket(en)"
                : "LKW " + truckIdentifier + " kann noch nicht abfahren: " + String.join("; ", issues);

        return new DepartureReadinessResponse(
                truckIdentifier,
                readyToDepart,
                activeOrders.size(),
                readyCount,
                notReadyCount,
                orderDetails,
                issues,
                message
        );
    }

    /**
     * Starts the departure for a truck.
     * Only IN_TRUCK orders are included in the route. PACKED_IN_WAREHOUSE orders
     * (not yet loaded/confirmed by the driver) are removed from the truck assignment
     * and made available for future routing.
     * DELIVERED/CANCELLED orders from previous trips are completely ignored.
     */
    @Transactional
    public StartDepartureResponse startDeparture(String truckIdentifier, User currentUser) {
        WarehouseTruck truck = resolveTruck(truckIdentifier);
        List<Order> allTruckOrders = loadTruckOrders(truckIdentifier);

        // Reject if already departed
        boolean hasShipped = allTruckOrders.stream()
                .anyMatch(order -> order.getStatus() == OrderStatus.SHIPPED);
        if (hasShipped || truck.getDepartureTime() != null || truck.getStatus() == TruckStatus.DEPARTED) {
            throw new IllegalStateException("Truck " + truckIdentifier + " has already departed");
        }

        // Separate active orders into ready (IN_TRUCK) and not-ready (PACKED_IN_WAREHOUSE)
        List<Order> readyOrders = allTruckOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.IN_TRUCK)
                .toList();

        List<Order> notReadyOrders = allTruckOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PACKED_IN_WAREHOUSE)
                .toList();

        if (readyOrders.isEmpty()) {
            throw new IllegalStateException("Truck " + truckIdentifier + " has no loaded orders (IN_TRUCK) to depart with");
        }

        // Remove not-ready orders from truck assignment (driver rejected / not loaded)
        List<StartDepartureResponse.SkippedOrderSummary> skippedSummaries = new ArrayList<>();
        if (!notReadyOrders.isEmpty()) {
            for (Order skippedOrder : notReadyOrders) {
                skippedOrder.setTruckIdentifier(null);
                skippedOrder.setTruckAssignedAt(null);
                skippedOrder.setRouteOptimizationId(null);
                skippedSummaries.add(new StartDepartureResponse.SkippedOrderSummary(
                        skippedOrder.getId(),
                        skippedOrder.getOrderNumber(),
                        "Nicht verladen – wird für zukünftige Routen verfügbar gemacht"
                ));
            }
            orderRepository.saveAll(notReadyOrders);
        }

        // Advance all ready orders to SHIPPED
        readyOrders.forEach(order -> order.setStatus(OrderStatus.SHIPPED));
        orderRepository.saveAll(readyOrders);

        // Update truck state
        Instant departureTime = Instant.now();
        truck.setDepartureTime(departureTime);
        truck.setCurrentWarehouseLocation(null);
        truck.setStatus(TruckStatus.DEPARTED);
        truck.setCompletedAt(null);
        warehouseTruckRepository.save(truck);

        // Build response
        List<StartDepartureResponse.ShippedOrderSummary> shippedSummaries = readyOrders.stream()
                .map(order -> new StartDepartureResponse.ShippedOrderSummary(
                        order.getId(),
                        order.getOrderNumber(),
                        order.getDeliveryCity(),
                        order.getDeliveryPostalCode()
                ))
                .toList();

        auditLogService.record(
                currentUser, "TRUCK_DEPARTED", "WarehouseTruck", truck.getId(),
                currentUser == null ? AuditInitiator.SYSTEM : AuditInitiator.USER,
                "Truck " + truckIdentifier + " departed with " + readyOrders.size() + " order(s)"
                        + (skippedSummaries.isEmpty() ? "" : ", " + skippedSummaries.size() + " order(s) removed from route"));

        String message = String.format("LKW %s ist abgefahren mit %d Paket(en)%s",
                truckIdentifier,
                readyOrders.size(),
                skippedSummaries.isEmpty() ? "" : " (" + skippedSummaries.size() + " Paket(e) aus Route entfernt)");

        return new StartDepartureResponse(
                true,
                truckIdentifier,
                readyOrders.size(),
                skippedSummaries.size(),
                departureTime,
                shippedSummaries,
                skippedSummaries,
                message
        );
    }

    @Transactional
    public WarehouseTransferResponse transferStock(WarehouseTransferRequest request) {
        if (request.fromWarehouseLocationId().equals(request.toWarehouseLocationId())) {
            throw new IllegalArgumentException("Quell- und Ziellager müssen unterschiedlich sein");
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + request.productId()));
        WarehouseLocation fromWarehouse = resolveWarehouseLocation(request.fromWarehouseLocationId());
        WarehouseLocation toWarehouse = resolveWarehouseLocation(request.toWarehouseLocationId());
        WarehouseProductStock fromStock = getOrCreateStock(product, fromWarehouse);
        WarehouseProductStock toStock = getOrCreateStock(product, toWarehouse);

        if (fromStock.getQuantity() < request.quantity()) {
            throw new IllegalArgumentException("Im Quelllager sind nur " + fromStock.getQuantity()
                    + " Stück von " + product.getName() + " verfügbar");
        }

        fromStock.setQuantity(fromStock.getQuantity() - request.quantity());
        toStock.setQuantity(toStock.getQuantity() + request.quantity());
        warehouseProductStockRepository.saveAll(List.of(fromStock, toStock));
        recalculateFulfillmentWarehousesForEligibleOrders(null, "stock transfer " + fromWarehouse.getCode() + " -> " + toWarehouse.getCode());

        return new WarehouseTransferResponse(
                product.getId(),
                product.getName(),
                toLocationResponse(fromWarehouse),
                toLocationResponse(toWarehouse),
                request.quantity(),
                fromStock.getQuantity(),
                toStock.getQuantity()
        );
    }

    private WarehouseLocation ensureFulfillmentWarehouse(Order order) {
        if (order.getFulfillmentWarehouse() != null) {
            return order.getFulfillmentWarehouse();
        }

        WarehouseLocation bestWarehouse = chooseBestFulfillmentWarehouse(order).orElse(null);
        if (bestWarehouse != null) {
            order.setFulfillmentWarehouse(bestWarehouse);
            return bestWarehouse;
        }

        WarehouseLocation mainWarehouse = resolveMainWarehouse();
        order.setFulfillmentWarehouse(mainWarehouse);
        return mainWarehouse;
    }

    private WarehouseLocation resolveWarehouseForOrder(Order order, Long requestedWarehouseLocationId) {
        if (requestedWarehouseLocationId != null) {
            return resolveWarehouseLocation(requestedWarehouseLocationId);
        }

        WarehouseLocation bestWarehouse = chooseBestFulfillmentWarehouse(order).orElse(null);
        if (bestWarehouse != null) {
            return bestWarehouse;
        }

        return ensureFulfillmentWarehouse(order);
    }

    private Optional<WarehouseLocation> chooseBestFulfillmentWarehouse(Order order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            return Optional.empty();
        }

        List<WarehouseLocation> activeWarehouses = warehouseLocationRepository.findActiveLocations();
        if (activeWarehouses.isEmpty()) {
            return Optional.empty();
        }

        GeocodedAddressResponse customerCoordinates = resolveOrderCoordinates(order).orElse(null);
        Map<Long, GeocodedAddressResponse> warehouseCoordinatesCache = new HashMap<>();

        List<WarehouseDistanceCandidate> candidates = new ArrayList<>();
        for (WarehouseLocation warehouse : activeWarehouses) {
            Map<Long, Integer> stockByProductId = resolveWarehouseStockByProductId(order, warehouse);
            boolean hasAllItems = order.getItems().stream()
                    .allMatch(item -> stockByProductId.getOrDefault(item.getProduct().getId(), 0) >= item.getQuantity());
            if (!hasAllItems) {
                continue;
            }

            double distanceKm = Double.POSITIVE_INFINITY;
            if (customerCoordinates != null) {
                GeocodedAddressResponse warehouseCoordinates = warehouseCoordinatesCache.computeIfAbsent(
                        warehouse.getId(),
                        id -> resolveWarehouseCoordinates(warehouse).orElse(null)
                );
                if (warehouseCoordinates != null) {
                    distanceKm = calculateDistanceKm(customerCoordinates, warehouseCoordinates);
                }
            }
            candidates.add(new WarehouseDistanceCandidate(warehouse, distanceKm));
        }

        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble(WarehouseDistanceCandidate::distanceKm)
                        .thenComparing(candidate -> !candidate.warehouse().isMainLocation())
                        .thenComparing(candidate -> candidate.warehouse().getId()))
                .map(WarehouseDistanceCandidate::warehouse)
                .findFirst();
    }

    private Optional<GeocodedAddressResponse> resolveOrderCoordinates(Order order) {
        DeliverySnapshot snapshot = resolveDeliverySnapshot(order);
        return addressLookupService.geocodeAddress(
                snapshot.street(),
                snapshot.postalCode(),
                snapshot.city(),
                snapshot.country()
        );
    }

    private Optional<GeocodedAddressResponse> resolveWarehouseCoordinates(WarehouseLocation warehouseLocation) {
        if (warehouseLocation.getLatitude() != null && warehouseLocation.getLongitude() != null) {
            return Optional.of(new GeocodedAddressResponse(
                    warehouseLocation.getName(),
                    warehouseLocation.getStreet(),
                    warehouseLocation.getPostalCode(),
                    warehouseLocation.getCity(),
                    warehouseLocation.getCountry(),
                    warehouseLocation.getLatitude(),
                    warehouseLocation.getLongitude()
            ));
        }

        return addressLookupService.geocodeAddress(
                warehouseLocation.getStreet(),
                warehouseLocation.getPostalCode(),
                warehouseLocation.getCity(),
                warehouseLocation.getCountry()
        );
    }

    private void recalculateFulfillmentWarehousesForEligibleOrders(User currentUser, String reason) {
        List<Order> orders = orderRepository.findByStatusInOrderByCreatedAtAsc(WAREHOUSE_REASSIGNABLE_STATUSES)
                .stream()
                .filter(this::isEligibleForWarehouseReassignment)
                .toList();

        if (orders.isEmpty()) {
            return;
        }

        List<Order> changedOrders = new ArrayList<>();
        for (Order order : orders) {
            WarehouseLocation previousWarehouse = order.getFulfillmentWarehouse();
            WarehouseLocation bestWarehouse = chooseBestFulfillmentWarehouse(order).orElse(previousWarehouse);
            if (bestWarehouse == null) {
                continue;
            }

            if (previousWarehouse == null || !Objects.equals(previousWarehouse.getId(), bestWarehouse.getId())) {
                order.setFulfillmentWarehouse(bestWarehouse);
                changedOrders.add(order);
                auditLogService.record(
                        currentUser,
                        "ORDER_WAREHOUSE_REASSIGNED",
                        "Order",
                        order.getId(),
                        currentUser == null ? AuditInitiator.SYSTEM : AuditInitiator.USER,
                        "Order reassigned to warehouse " + bestWarehouse.getCode() + " after " + reason
                );
            }
        }

        if (!changedOrders.isEmpty()) {
            orderRepository.saveAll(changedOrders);
        }
    }

    private boolean isEligibleForWarehouseReassignment(Order order) {
        if (order.getPackedAt() != null || order.getStatus() == OrderStatus.PACKED_IN_WAREHOUSE) {
            return false;
        }
        return order.getItems() != null
                && !order.getItems().isEmpty()
                && order.getItems().stream().allMatch(item -> item.getPickedAt() == null);
    }

    private WarehouseLocation resolveWarehouseLocation(Long warehouseLocationId) {
        return warehouseLocationRepository.findById(warehouseLocationId)
                .filter(WarehouseLocation::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Warehouse location not found: " + warehouseLocationId));
    }

    private WarehouseLocation resolveMainWarehouse() {
        return warehouseLocationRepository.findFirstByMainLocationTrueAndActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new EntityNotFoundException("No active main warehouse configured"));
    }

    private WarehouseProductStock getOrCreateStock(Product product, WarehouseLocation warehouseLocation) {
        return warehouseProductStockRepository
                .findByProductIdAndWarehouseLocationId(product.getId(), warehouseLocation.getId())
                .orElseGet(() -> {
                    WarehouseProductStock stock = new WarehouseProductStock();
                    stock.setProduct(product);
                    stock.setWarehouseLocation(warehouseLocation);
                    stock.setQuantity(warehouseLocation.isMainLocation() ? product.getStock() : 0);
                    return stock;
                });
    }

    private Map<Long, Integer> resolveWarehouseStockByProductId(Order order, WarehouseLocation warehouseLocation) {
        List<Long> productIds = order.getItems().stream()
                .map(orderItem -> orderItem.getProduct().getId())
                .distinct()
                .toList();

        if (productIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> persistedStock = warehouseProductStockRepository
                .findByWarehouseLocationIdAndProductIdIn(warehouseLocation.getId(), productIds)
                .stream()
                .collect(Collectors.toMap(
                        stock -> stock.getProduct().getId(),
                        WarehouseProductStock::getQuantity
                ));

        Map<Long, Integer> resolvedStock = new LinkedHashMap<>(persistedStock);
        if (warehouseLocation.isMainLocation()) {
            order.getItems().forEach(orderItem -> resolvedStock.putIfAbsent(
                    orderItem.getProduct().getId(),
                    orderItem.getProduct().getStock()
            ));
        }

        productIds.forEach(productId -> resolvedStock.putIfAbsent(productId, 0));
        return resolvedStock;
    }

    private List<String> buildWarehouseWarnings(
            Order order,
            WarehouseLocation warehouseLocation,
            Map<Long, Integer> stockByProductId
    ) {
        List<String> warnings = new ArrayList<>();
        order.getItems().forEach(orderItem -> {
            int stock = stockByProductId.getOrDefault(orderItem.getProduct().getId(), 0);
            if (stock < orderItem.getQuantity()) {
                warnings.add(orderItem.getProduct().getName() + ": benötigt "
                        + orderItem.getQuantity() + ", im Lager " + warehouseLocation.getName()
                        + " verfügbar " + stock);
            }
        });
        return warnings;
    }

    private void deductWarehouseStock(Order order, WarehouseLocation warehouseLocation) {
        List<WarehouseProductStock> updatedStocks = new ArrayList<>();

        order.getItems().forEach(orderItem -> {
            WarehouseProductStock stock = getOrCreateStock(orderItem.getProduct(), warehouseLocation);
            int requiredQuantity = resolveRequiredQuantity(orderItem);
            if (stock.getQuantity() < requiredQuantity) {
                throw new IllegalStateException("Nicht ausreichend Bestand im Lager "
                        + warehouseLocation.getName() + " für " + orderItem.getProduct().getName()
                        + ": benötigt " + requiredQuantity
                        + ", verfügbar " + stock.getQuantity());
            }

            stock.setQuantity(stock.getQuantity() - requiredQuantity);
            updatedStocks.add(stock);
        });

        warehouseProductStockRepository.saveAll(updatedStocks);
    }

    private WarehouseLocationResponse toLocationResponse(WarehouseLocation warehouseLocation) {
        return new WarehouseLocationResponse(
                warehouseLocation.getId(),
                warehouseLocation.getCode(),
                warehouseLocation.getName(),
                warehouseLocation.getStreet(),
                warehouseLocation.getPostalCode(),
                warehouseLocation.getCity(),
                warehouseLocation.getCountry(),
                warehouseLocation.isMainLocation(),
                warehouseLocation.getLatitude(),
                warehouseLocation.getLongitude(),
                warehouseLocation.isActive()
        );
    }

    private WarehouseTruckResponse toTruckResponse(WarehouseTruck truck) {
        List<Order> truckOrders = loadTruckOrders(truck.getTruckIdentifier());
        TruckStatus dynamicStatus = deriveDynamicTruckStatus(truck, truckOrders);
        int assignedOrderCount = countAssignedOrders(truckOrders);
        int remainingCapacity = Math.max(0, truck.getCapacityOrders() - assignedOrderCount);
        boolean onRoute = isTruckOnRoute(dynamicStatus, truckOrders);

        return new WarehouseTruckResponse(
                truck.getTruckIdentifier(),
                dynamicStatus,
                truck.getOriginWarehouseLocation() == null ? null : toLocationResponse(truck.getOriginWarehouseLocation()),
                truck.getCurrentWarehouseLocation() == null ? null : toLocationResponse(truck.getCurrentWarehouseLocation()),
                truck.getDriverId(),
                truck.getDepartureTime(),
                truck.getCompletedAt(),
                truck.getRouteOptimizationId(),
                truck.getCurrentLatitude(),
                truck.getCurrentLongitude(),
                truck.getCapacityOrders(),
                assignedOrderCount,
                remainingCapacity,
                onRoute,
                buildTruckLocationLabel(truck, onRoute)
        );
    }

    private AutoAssignTruckRouteResponse buildAutoAssignTruckRouteResponse(
            WarehouseTruck truck,
            WarehouseLocation warehouse,
            TruckAssignmentCluster cluster
    ) {
        List<RoadRouteStopRequest> stops = new ArrayList<>();
        if (warehouse.getLatitude() != null && warehouse.getLongitude() != null) {
            stops.add(new RoadRouteStopRequest(
                    "warehouse-" + warehouse.getId(),
                    warehouse.getName(),
                    warehouse.getLatitude(),
                    warehouse.getLongitude()
            ));
        }

        cluster.orders().forEach(candidate -> {
            GeocodedAddressResponse location = candidate.location();
            if (location != null) {
                stops.add(new RoadRouteStopRequest(
                        "order-" + candidate.order().getId(),
                        candidate.order().getOrderNumber(),
                        location.latitude(),
                        location.longitude()
                ));
            }
        });

        String distance = null;
        String duration = null;
        if (stops.size() >= 2) {
            RoadRouteRequest routeRequest = new RoadRouteRequest(stops, Boolean.FALSE);
            RoadRouteResponse routeResponse = addressLookupService.routeTrip(routeRequest).orElse(null);
            if (routeResponse != null) {
                distance = String.format(Locale.ROOT, "%.1f km", routeResponse.distanceMeters() / 1000.0);
                duration = formatDuration(routeResponse.durationSeconds());
            }
        }

        return new AutoAssignTruckRouteResponse(
                truck.getTruckIdentifier(),
                cluster.orders().size(),
                distance,
                duration,
                truck.getRouteOptimizationId()
        );
    }

    private String formatDuration(double seconds) {
        long totalMinutes = Math.round(seconds / 60.0);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private String buildTruckLocationLabel(WarehouseTruck truck, boolean onRoute) {
        if (onRoute) {
            return "Auf Route";
        }
        if (truck.getCurrentWarehouseLocation() != null) {
            return truck.getCurrentWarehouseLocation().getName() + " (Lager)";
        }
        if (truck.getOriginWarehouseLocation() != null) {
            return truck.getOriginWarehouseLocation().getName() + " (bereit)";
        }
        return "Unbekannt";
    }

    private WarehouseTruck resolveTruck(String truckIdentifier) {
        return warehouseTruckRepository.findByTruckIdentifier(truckIdentifier)
                .orElseThrow(() -> new EntityNotFoundException("Truck not found: " + truckIdentifier));
    }

    private void ensureTruckIsAtWarehouse(WarehouseTruck truck, WarehouseLocation warehouseLocation) {
        if (truck.getCurrentWarehouseLocation() != null
                && !Objects.equals(truck.getCurrentWarehouseLocation().getId(), warehouseLocation.getId())) {
            throw new IllegalStateException("Truck " + truck.getTruckIdentifier()
                    + " is currently located at another warehouse");
        }
    }

    private void ensureTruckIsAssignable(WarehouseTruck truck) {
        if (truck.getDepartureTime() != null || truck.getStatus() == TruckStatus.DEPARTED) {
            throw new IllegalStateException("Truck " + truck.getTruckIdentifier() + " has already departed");
        }
        boolean hasShippedOrders = loadTruckOrders(truck.getTruckIdentifier()).stream()
                .anyMatch(order -> order.getStatus() == OrderStatus.SHIPPED);
        if (hasShippedOrders) {
            throw new IllegalStateException("Truck " + truck.getTruckIdentifier() + " has already departed");
        }
    }

    private void refreshTruckState(WarehouseTruck truck) {
        List<Order> truckOrders = loadTruckOrders(truck.getTruckIdentifier());
        TruckStatus dynamicStatus = deriveDynamicTruckStatus(truck, truckOrders);
        truck.setStatus(dynamicStatus);
        if (dynamicStatus == TruckStatus.AVAILABLE) {
            truck.setRouteOptimizationId(null);
            if (truck.getDepartureTime() == null) {
                truck.setCurrentWarehouseLocation(truck.getOriginWarehouseLocation());
            }
        }
        warehouseTruckRepository.save(truck);
    }

    private List<Order> loadTruckOrders(String truckIdentifier) {
        return orderRepository.findByTruckIdentifierOrderByCreatedAtAsc(truckIdentifier);
    }

    private int countAssignedOrders(List<Order> truckOrders) {
        return (int) truckOrders.stream()
                .filter(order -> TRUCK_ASSIGNED_COUNT_STATUSES.contains(order.getStatus()))
                .count();
    }

    private TruckStatus deriveDynamicTruckStatus(WarehouseTruck truck, List<Order> truckOrders) {
        if (truckOrders.isEmpty()) {
            return TruckStatus.AVAILABLE;
        }

        boolean hasShipped = truckOrders.stream().anyMatch(order -> order.getStatus() == OrderStatus.SHIPPED);
        if (hasShipped) {
            return TruckStatus.DEPARTED;
        }

        boolean hasInTruck = truckOrders.stream().anyMatch(order -> order.getStatus() == OrderStatus.IN_TRUCK);
        if (hasInTruck) {
            return TruckStatus.LOADED;
        }

        boolean allDelivered = truckOrders.stream().allMatch(order -> order.getStatus() == OrderStatus.DELIVERED);
        if (allDelivered) {
            return TruckStatus.COMPLETED;
        }

        return TruckStatus.AVAILABLE;
    }

    private boolean isTruckOnRoute(TruckStatus dynamicStatus, List<Order> truckOrders) {
        return dynamicStatus == TruckStatus.DEPARTED
                || truckOrders.stream().anyMatch(order -> order.getStatus() == OrderStatus.SHIPPED);
    }

    private void ensureTruckReadyForDeparture(String truckIdentifier, WarehouseTruck truck) {
        List<Order> allTruckOrders = loadTruckOrders(truckIdentifier);

        // Filter out completed orders (DELIVERED, CANCELLED) — they are from previous trips
        List<Order> activeOrders = allTruckOrders.stream()
                .filter(order -> TRUCK_DEPARTURE_RELEVANT_STATUSES.contains(order.getStatus()))
                .toList();

        if (activeOrders.isEmpty()) {
            throw new IllegalStateException("Truck " + truckIdentifier + " has no assigned orders to depart");
        }

        boolean hasShipped = allTruckOrders.stream().anyMatch(order -> order.getStatus() == OrderStatus.SHIPPED);
        if (hasShipped || truck.getDepartureTime() != null || truck.getStatus() == TruckStatus.DEPARTED) {
            throw new IllegalStateException("Truck " + truckIdentifier + " has already departed");
        }

        // Check that ALL active orders are IN_TRUCK (ready for departure)
        List<Order> notReadyOrders = activeOrders.stream()
                .filter(order -> order.getStatus() != OrderStatus.IN_TRUCK)
                .toList();

        if (!notReadyOrders.isEmpty()) {
            String orderSummary = notReadyOrders.stream()
                    .map(order -> order.getOrderNumber() + " (" + order.getStatus() + ")")
                    .limit(5)
                    .toList()
                    .toString();
            int totalNotReady = notReadyOrders.size();
            String message = String.format(
                    "Truck %s cannot depart: %d order(s) are not ready. Not ready orders: %s%s",
                    truckIdentifier,
                    totalNotReady,
                    orderSummary,
                    totalNotReady > 5 ? "... and " + (totalNotReady - 5) + " more" : ""
            );
            throw new IllegalStateException(message);
        }
    }

    private WarehouseTruck resolveTruckForCluster(
            TruckAssignmentCluster cluster,
            WarehouseLocation warehouse,
            List<WarehouseTruck> availableTrucks,
            Map<String, Integer> provisionalLoadByTruckIdentifier
    ) {
        String existingTruckIdentifier = cluster.resolveTruckIdentifier();
        if (existingTruckIdentifier != null) {
            WarehouseTruck existingTruck = warehouseTruckRepository.findByTruckIdentifier(existingTruckIdentifier).orElse(null);
            if (existingTruck != null && existingTruck.getDepartureTime() == null) {
                ensureTruckIsAtWarehouse(existingTruck, warehouse);
                return existingTruck;
            }
        }

        int requiredCapacity = cluster.orders().size();
        WarehouseTruck truck = availableTrucks.stream()
                .filter(candidate -> candidate.getDepartureTime() == null)
                .filter(candidate -> Math.max(0,
                        candidate.getCapacityOrders()
                                - countActiveOrdersOnTruck(candidate.getTruckIdentifier())
                                - provisionalLoadByTruckIdentifier.getOrDefault(candidate.getTruckIdentifier(), 0)) >= requiredCapacity)
                .findFirst()
                .orElseGet(() -> createTruckForWarehouse(warehouse));

        ensureTruckIsAtWarehouse(truck, warehouse);
        truck.setStatus(TruckStatus.LOADED);
        truck.setCurrentWarehouseLocation(warehouse);
        truck.setOriginWarehouseLocation(warehouse);
        if (truck.getRouteOptimizationId() == null || truck.getRouteOptimizationId().isBlank()) {
            truck.setRouteOptimizationId(java.util.UUID.randomUUID().toString());
        }
        return truck;
    }

    private int countActiveOrdersOnTruck(String truckIdentifier) {
        return (int) orderRepository.findByTruckIdentifierOrderByCreatedAtAsc(truckIdentifier).stream()
                .filter(order -> order.getStatus() == OrderStatus.PACKED_IN_WAREHOUSE || order.getStatus() == OrderStatus.IN_TRUCK)
                .count();
    }

    private WarehouseTruck createTruckForWarehouse(WarehouseLocation warehouse) {
        WarehouseTruck truck = new WarehouseTruck();
        truck.setTruckIdentifier(generateTruckIdentifier(warehouse));
        truck.setOriginWarehouseLocation(warehouse);
        truck.setCurrentWarehouseLocation(warehouse);
        truck.setStatus(TruckStatus.AVAILABLE);
        truck.setCapacityOrders(10);
        return warehouseTruckRepository.save(truck);
    }

    private String generateTruckIdentifier(WarehouseLocation warehouse) {
        String prefix = warehouse.getCode() != null && !warehouse.getCode().isBlank()
                ? warehouse.getCode().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT)
                : "TRUCK";
        long existingCount = warehouseTruckRepository.findByOriginWarehouseLocationIdOrderByTruckIdentifierAsc(warehouse.getId()).size();
        return String.format(Locale.ROOT, "LKW-%s-%03d", prefix, existingCount + 1);
    }

    private OrderStatus determineNextStatus(Order order) {
        boolean clickAndCollect = isClickAndCollectOrder(order);

        return switch (order.getStatus()) {
            case PENDING -> OrderStatus.CONFIRMED;
            case CONFIRMED -> OrderStatus.PACKED_IN_WAREHOUSE;
            case PACKED_IN_WAREHOUSE -> clickAndCollect ? OrderStatus.READY_FOR_PICKUP : OrderStatus.IN_TRUCK;
            case READY_FOR_PICKUP -> OrderStatus.DELIVERED;
            case IN_TRUCK -> OrderStatus.SHIPPED;
            case SHIPPED -> OrderStatus.DELIVERED;
            case Pending_Approval, Rejected -> null;
            case DELIVERED, CANCELLED -> null;
        };
    }

    private boolean isClickAndCollectOrder(Order order) {
        return order.getPickupStore() != null;
    }

    private String normalizeTruckIdentifier(String requestedTruckIdentifier, String existingTruckIdentifier) {
        if (requestedTruckIdentifier != null && !requestedTruckIdentifier.isBlank()) {
            return requestedTruckIdentifier.trim();
        }
        if (existingTruckIdentifier != null && !existingTruckIdentifier.isBlank()) {
            return existingTruckIdentifier.trim();
        }
        return null;
    }

    private Map<String, String> buildRegionLabels(List<Order> orders) {
        return orders.stream()
                .collect(Collectors.groupingBy(
                        this::resolveRegionKey,
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> buildRegionLabel(entry.getKey(), entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, String> buildSuggestedTruckIdentifiers(List<Order> orders) {
        Map<String, String> activeTruckByRegion = orders.stream()
                .filter(order -> order.getTruckIdentifier() != null && !order.getTruckIdentifier().isBlank())
                .sorted(Comparator.comparing(Order::getCreatedAt))
                .collect(Collectors.toMap(
                        this::resolveRegionKey,
                        order -> order.getTruckIdentifier().trim(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<String, String> suggestedIdentifiers = new LinkedHashMap<>();
        orders.forEach(order -> {
            String regionKey = resolveRegionKey(order);
            String activeTruck = activeTruckByRegion.get(regionKey);
            if (activeTruck != null) {
                suggestedIdentifiers.put(regionKey, activeTruck);
                return;
            }

            suggestedIdentifiers.putIfAbsent(regionKey, createSuggestedTruckIdentifier(order));
        });

        return suggestedIdentifiers;
    }

    private String resolveRegionKey(Order order) {
        DeliverySnapshot deliverySnapshot = resolveDeliverySnapshot(order);
        String normalizedCountry = normalizeRouteToken(deliverySnapshot.country(), "route");
        String postalPrefix = resolvePostalPrefix(deliverySnapshot.postalCode());
        if (postalPrefix != null) {
            return normalizedCountry + "-plz-" + postalPrefix;
        }

        String normalizedCity = normalizeRouteToken(deliverySnapshot.city(), "city");
        return normalizedCountry + "-city-" + normalizedCity;
    }

    private String buildRegionLabel(String regionKey, List<Order> regionOrders) {
        String country = regionOrders.stream()
                .map(this::resolveDeliverySnapshot)
                .map(DeliverySnapshot::country)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .findFirst()
                .orElse("Unbekannt");

        String postalPrefix = regionOrders.stream()
                .map(this::resolveDeliverySnapshot)
                .map(DeliverySnapshot::postalCode)
                .map(this::resolvePostalPrefix)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        String citySummary = regionOrders.stream()
                .map(this::resolveDeliverySnapshot)
                .map(DeliverySnapshot::city)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted()
                .limit(3)
                .collect(Collectors.joining(", "));

        if (postalPrefix != null && !citySummary.isEmpty()) {
            return country + " · PLZ " + postalPrefix + " · " + citySummary;
        }
        if (postalPrefix != null) {
            return country + " · PLZ " + postalPrefix;
        }
        if (!citySummary.isEmpty()) {
            return country + " · " + citySummary;
        }
        return "Unbekannte Route · " + regionKey;
    }

    private String resolvePostalPrefix(String postalCode) {
        if (postalCode == null) {
            return null;
        }

        String digits = postalCode.replaceAll("\\D", "");
        if (digits.length() >= 2) {
            return digits.substring(0, 2);
        }
        if (!digits.isEmpty()) {
            return digits;
        }
        return null;
    }

    private String createSuggestedTruckIdentifier(Order order) {
        DeliverySnapshot deliverySnapshot = resolveDeliverySnapshot(order);
        String postalPrefix = resolvePostalPrefix(deliverySnapshot.postalCode());
        if (postalPrefix != null) {
            return "LKW-" + postalPrefix;
        }

        String city = normalizeRouteToken(deliverySnapshot.city(), "ROUTE").toUpperCase(Locale.ROOT);
        return city.length() > 8 ? "LKW-" + city.substring(0, 8) : "LKW-" + city;
    }

    private String normalizeRouteToken(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{Alnum}]+", "-")
                .replaceAll("^-+|-+$", "")
                .toLowerCase(Locale.ROOT);

        return normalized.isBlank() ? fallback : normalized;
    }

    private WarehouseOrderResponse toResponse(
            Order order,
            String regionKey,
            Map<String, String> regionLabels,
            Map<String, String> suggestedTruckIdentifiers
    ) {
        DeliverySnapshot deliverySnapshot = resolveDeliverySnapshot(order);
        WarehouseLocation fulfillmentWarehouse = ensureFulfillmentWarehouse(order);
        Map<Long, Integer> stockByProductId = resolveWarehouseStockByProductId(order, fulfillmentWarehouse);
        List<String> warehouseWarnings = buildWarehouseWarnings(order, fulfillmentWarehouse, stockByProductId);
        boolean clickAndCollect = isClickAndCollectOrder(order);
        List<WarehouseOrderItemResponse> items = order.getItems().stream()
                .map(orderItem -> new WarehouseOrderItemResponse(
                        orderItem.getId(),
                        orderItem.getProduct().getId(),
                        orderItem.getProduct().getName(),
                        orderItem.getPersonalizationText(),
                        orderItem.getQuantity(),
                        orderItem.getProduct().getStock(),
                        stockByProductId.getOrDefault(orderItem.getProduct().getId(), 0),
                        orderItem.getProduct().getWarehousePosition(),
                        orderItem.getPickedAt(),
                        orderItem.getPickedByUserId(),
                        orderItem.getPickedQuantity()
                ))
                .toList();

        String deliveryAddress = Arrays.asList(
                        deliverySnapshot.street(),
                        deliverySnapshot.postalCode(),
                        deliverySnapshot.city(),
                        deliverySnapshot.country())
                .stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(", "));

        boolean plusMember = order.getCustomer() != null
                && subscriptionService.hasActivePlusSubscription(order.getCustomer().getId());

        return new WarehouseOrderResponse(
                order.getId(),
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getStatus(),
                plusMember,
                regionKey,
                regionLabels.getOrDefault(regionKey, "Unbekannte Route"),
                order.getTruckIdentifier(),
                clickAndCollect ? null : suggestedTruckIdentifiers.get(regionKey),
                toLocationResponse(fulfillmentWarehouse),
                warehouseWarnings.isEmpty(),
                warehouseWarnings,
                order.getShippingMethod(),
                clickAndCollect,
                deliveryAddress,
                deliverySnapshot.street(),
                deliverySnapshot.city(),
                deliverySnapshot.postalCode(),
                deliverySnapshot.country(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items
        );
    }

    private void completePackingInternal(Order order, Long warehouseLocationId, User currentUser) {
        ensureStatus(order, OrderStatus.CONFIRMED, "Only confirmed orders can be completed in warehouse");
        validateAllItemsPicked(order);

        WarehouseLocation fulfillmentWarehouse = resolveWarehouseForOrder(order, warehouseLocationId);
        order.setFulfillmentWarehouse(fulfillmentWarehouse);
        deductWarehouseStock(order, fulfillmentWarehouse);
        order.setStatus(OrderStatus.PACKED_IN_WAREHOUSE);
        if (order.getPackingStartedAt() == null) {
            order.setPackingStartedAt(Instant.now());
        }
        order.setPackedAt(Instant.now());
        order.setPackedByUserId(resolveUserIdentifier(currentUser));

        Order savedOrder = orderRepository.save(order);
        auditLogService.record(
                currentUser,
                "ORDER_PACKED",
                "Order",
                savedOrder.getId(),
                currentUser == null ? AuditInitiator.SYSTEM : AuditInitiator.USER,
                "Order packed in warehouse and marked as PACKED_IN_WAREHOUSE"
        );
        eventPublisher.publishEvent(new WarehouseOrderPackedEvent(savedOrder.getId()));
        recalculateFulfillmentWarehousesForEligibleOrders(currentUser, "packing completion for order " + savedOrder.getId());
    }

    private int resolvePickedQuantity(PickOrderItemRequest request, OrderItem item) {
        if (!request.picked()) {
            return 0;
        }
        int pickedQuantity = request.pickedQuantity() != null ? request.pickedQuantity() : item.getQuantity();
        if (pickedQuantity < 1) {
            throw new IllegalArgumentException("Picked quantity must be at least 1");
        }
        if (pickedQuantity > item.getQuantity()) {
            throw new IllegalArgumentException("Picked quantity must not exceed ordered quantity (" + item.getQuantity() + ")");
        }
        return pickedQuantity;
    }

    private int resolveRequiredQuantity(OrderItem item) {
        return item.getPickedQuantity() != null ? item.getPickedQuantity() : item.getQuantity();
    }

    private void validateAllItemsPicked(Order order) {
        List<String> issues = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            if (item.getPickedAt() == null) {
                issues.add(item.getProduct().getName() + " (not picked)");
            } else if (item.getPickedQuantity() == null || item.getPickedQuantity() < item.getQuantity()) {
                int picked = item.getPickedQuantity() != null ? item.getPickedQuantity() : 0;
                issues.add(item.getProduct().getName() + " (picked " + picked + "/" + item.getQuantity() + ")");
            }
        }

        if (!issues.isEmpty()) {
            throw new IllegalStateException(
                    "All order items must be fully picked before packing can be completed. "
                            + "Issues: " + String.join(", ", issues));
        }
    }

    private OrderItem findOrderItem(Order order, Long itemId) {
        return order.getItems().stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Order item not found: " + itemId));
    }

    private void ensureStatus(Order order, OrderStatus requiredStatus, String message) {
        if (order.getStatus() != requiredStatus) {
            throw new IllegalStateException(message);
        }
    }

    private String resolveUserIdentifier(User user) {
        if (user == null) {
            return "system";
        }
        if (user.getId() != null) {
            return String.valueOf(user.getId());
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername().trim();
        }
        return "unknown";
    }

    private DeliverySnapshot resolveDeliverySnapshot(Order order) {
        if (hasDeliverySnapshot(order.getDeliveryStreet(), order.getDeliveryCity(), order.getDeliveryPostalCode(), order.getDeliveryCountry())) {
            return new DeliverySnapshot(
                    trimToNull(order.getDeliveryStreet()),
                    trimToNull(order.getDeliveryCity()),
                    trimToNull(order.getDeliveryPostalCode()),
                    trimToNull(order.getDeliveryCountry())
            );
        }

        if (order.getCustomer() != null && order.getCustomer().getId() != null) {
            DeliveryAddress fallbackAddress = deliveryAddressRepository.findFirstByUserId(order.getCustomer().getId()).orElse(null);
            if (fallbackAddress != null) {
                return new DeliverySnapshot(
                        trimToNull(fallbackAddress.getStreet()),
                        trimToNull(fallbackAddress.getCity()),
                        trimToNull(fallbackAddress.getPostalCode()),
                        trimToNull(fallbackAddress.getCountry())
                );
            }
        }

        return new DeliverySnapshot(
                trimToNull(order.getDeliveryStreet()),
                trimToNull(order.getDeliveryCity()),
                trimToNull(order.getDeliveryPostalCode()),
                trimToNull(order.getDeliveryCountry())
        );
    }

    private boolean hasDeliverySnapshot(String street, String city, String postalCode, String country) {
        return trimToNull(street) != null
                || trimToNull(city) != null
                || trimToNull(postalCode) != null
                || trimToNull(country) != null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TruckAssignmentCandidate toTruckAssignmentCandidate(Order order) {
        DeliverySnapshot deliverySnapshot = resolveDeliverySnapshot(order);
        GeocodedAddressResponse geocodedAddress = addressLookupService.geocodeAddress(
                deliverySnapshot.street(),
                deliverySnapshot.postalCode(),
                deliverySnapshot.city(),
                deliverySnapshot.country()
        ).orElse(null);

        return new TruckAssignmentCandidate(
                order,
                deliverySnapshot,
                normalizeRouteToken(deliverySnapshot.country(), "route"),
                geocodedAddress
        );
    }

    private double calculateDistanceKm(GeocodedAddressResponse left, GeocodedAddressResponse right) {
        if (left == null || right == null) {
            return Double.POSITIVE_INFINITY;
        }

        double earthRadiusKm = 6371.0;
        double latitudeDistance = Math.toRadians(right.latitude() - left.latitude());
        double longitudeDistance = Math.toRadians(right.longitude() - left.longitude());
        double startLatitude = Math.toRadians(left.latitude());
        double endLatitude = Math.toRadians(right.latitude());

        double distanceFactor = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(startLatitude) * Math.cos(endLatitude)
                * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);

        return 2 * earthRadiusKm * Math.atan2(Math.sqrt(distanceFactor), Math.sqrt(1 - distanceFactor));
    }

    private final class TruckAssignmentCluster {
        private final List<TruckAssignmentCandidate> orders = new java.util.ArrayList<>();
        private final String normalizedCountry;

        private TruckAssignmentCluster(TruckAssignmentCandidate firstCandidate) {
            this.normalizedCountry = firstCandidate.normalizedCountry();
            this.orders.add(firstCandidate);
        }

        private boolean canInclude(TruckAssignmentCandidate candidate) {
            if (!normalizedCountry.equals(candidate.normalizedCountry())) {
                return false;
            }

            List<TruckAssignmentCandidate> geocodedOrders = orders.stream()
                    .filter(order -> order.location() != null)
                    .toList();

            if (candidate.location() != null && !geocodedOrders.isEmpty()) {
                return geocodedOrders.stream()
                        .allMatch(existingOrder ->
                                calculateDistanceKm(existingOrder.location(), candidate.location()) <= MAX_TRUCK_CLUSTER_DISTANCE_KM);
            }

            return orders.stream()
                    .map(order -> resolveRegionKey(order.order()))
                    .anyMatch(regionKey -> regionKey.equals(resolveRegionKey(candidate.order())));
        }

        private double distanceTo(TruckAssignmentCandidate candidate) {
            if (candidate.location() == null) {
                return Double.POSITIVE_INFINITY;
            }

            return orders.stream()
                    .map(TruckAssignmentCandidate::location)
                    .filter(Objects::nonNull)
                    .mapToDouble(location -> calculateDistanceKm(location, candidate.location()))
                    .average()
                    .orElse(Double.POSITIVE_INFINITY);
        }

        private void add(TruckAssignmentCandidate candidate) {
            orders.add(candidate);
        }

        private String resolveTruckIdentifier() {
            return orders.stream()
                    .map(TruckAssignmentCandidate::order)
                    .map(Order::getTruckIdentifier)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .findFirst()
                    .orElseGet(() -> createSuggestedTruckIdentifier(orders.get(0).order()));
        }

        private String buildClusterLabel() {
            List<Order> clusterOrders = orders.stream()
                    .map(TruckAssignmentCandidate::order)
                    .toList();

            String country = orders.stream()
                    .map(TruckAssignmentCandidate::snapshot)
                    .map(DeliverySnapshot::country)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .findFirst()
                    .orElse("Unbekannt");

            String cities = orders.stream()
                    .map(TruckAssignmentCandidate::snapshot)
                    .map(DeliverySnapshot::city)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .distinct()
                    .sorted()
                    .limit(3)
                    .collect(Collectors.joining(", "));

            if (!cities.isEmpty() && clusterOrders.size() > 1) {
                return country + " · " + cities;
            }

            return buildRegionLabel(resolveRegionKey(clusterOrders.get(0)), clusterOrders);
        }

        private List<TruckAssignmentCandidate> orders() {
            return orders;
        }
    }

    private record DeliverySnapshot(
            String street,
            String city,
            String postalCode,
            String country
    ) {}

    private record TruckAssignmentCandidate(
            Order order,
            DeliverySnapshot snapshot,
            String normalizedCountry,
            GeocodedAddressResponse location
    ) {}

    private record WarehouseDistanceCandidate(
            WarehouseLocation warehouse,
            double distanceKm
    ) {}
}
