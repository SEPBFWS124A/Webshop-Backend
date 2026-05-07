package de.fhdw.webshop.warehouse;

import de.fhdw.webshop.address.AddressLookupService;
import de.fhdw.webshop.address.GeocodedAddressResponse;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.user.DeliveryAddress;
import de.fhdw.webshop.user.DeliveryAddressRepository;
import de.fhdw.webshop.warehouse.dto.AutoAssignTruckIdentifiersResponse;
import de.fhdw.webshop.warehouse.dto.TruckAssignmentChangeResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseLocationResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseOrderItemResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseOrderResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseTransferRequest;
import de.fhdw.webshop.warehouse.dto.WarehouseTransferResponse;
import jakarta.persistence.EntityNotFoundException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private static final List<OrderStatus> ACTIVE_WAREHOUSE_STATUSES = List.of(
            OrderStatus.PENDING,
            OrderStatus.CONFIRMED,
            OrderStatus.PACKED_IN_WAREHOUSE,
            OrderStatus.IN_TRUCK,
            OrderStatus.SHIPPED
    );

    private static final List<OrderStatus> TRUCK_ASSIGNABLE_STATUSES = List.of(
            OrderStatus.PENDING,
            OrderStatus.CONFIRMED,
            OrderStatus.PACKED_IN_WAREHOUSE,
            OrderStatus.IN_TRUCK
    );
    private static final double MAX_TRUCK_CLUSTER_DISTANCE_KM = 120.0;

    private final OrderRepository orderRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final AddressLookupService addressLookupService;
    private final ProductRepository productRepository;
    private final WarehouseLocationRepository warehouseLocationRepository;
    private final WarehouseProductStockRepository warehouseProductStockRepository;

    @Transactional
    public List<WarehouseOrderResponse> listOrders(OrderStatus status) {
        return listOrders(status, null);
    }

    @Transactional
    public List<WarehouseOrderResponse> listOrders(OrderStatus status, Long warehouseLocationId) {
        List<Order> orders = status == null
                ? orderRepository.findByStatusNotInOrderByCreatedAtAsc(List.of(
                        OrderStatus.DELIVERED,
                        OrderStatus.CANCELLED,
                        OrderStatus.Pending_Approval,
                        OrderStatus.Rejected))
                : orderRepository.findByStatusOrderByCreatedAtAsc(status);

        List<Order> filteredOrders = orders.stream()
                .filter(order -> status != null || ACTIVE_WAREHOUSE_STATUSES.contains(order.getStatus()))
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

    @Transactional
    public WarehouseOrderResponse advanceOrder(Long orderId, String requestedTruckIdentifier) {
        return advanceOrder(orderId, requestedTruckIdentifier, null);
    }

    @Transactional
    public WarehouseOrderResponse advanceOrder(Long orderId, String requestedTruckIdentifier, Long warehouseLocationId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        OrderStatus nextStatus = determineNextStatus(order.getStatus());
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
        }

        WarehouseLocation fulfillmentWarehouse = resolveWarehouseForOrder(order, warehouseLocationId);
        order.setFulfillmentWarehouse(fulfillmentWarehouse);
        if (nextStatus == OrderStatus.PACKED_IN_WAREHOUSE) {
            deductWarehouseStock(order, fulfillmentWarehouse);
        }

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

        String resolvedTruckIdentifier = normalizeTruckIdentifier(requestedTruckIdentifier, null);
        if (resolvedTruckIdentifier == null) {
            throw new IllegalArgumentException("A truck identifier is required");
        }

        order.setTruckIdentifier(resolvedTruckIdentifier);
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
    public AutoAssignTruckIdentifiersResponse autoAssignTruckIdentifiers() {
        List<Order> activeOrders = orderRepository.findByStatusNotInOrderByCreatedAtAsc(
                List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.Pending_Approval, OrderStatus.Rejected)
        ).stream()
                .filter(order -> TRUCK_ASSIGNABLE_STATUSES.contains(order.getStatus()))
                .toList();

        List<TruckAssignmentCandidate> candidates = activeOrders.stream()
                .map(this::toTruckAssignmentCandidate)
                .sorted(Comparator.comparing(candidate -> candidate.order().getCreatedAt()))
                .toList();

        List<TruckAssignmentCluster> clusters = new java.util.ArrayList<>();
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

        List<TruckAssignmentChangeResponse> changes = new java.util.ArrayList<>();

        clusters.forEach(cluster -> {
            String resolvedTruckIdentifier = cluster.resolveTruckIdentifier();
            String regionLabel = cluster.buildClusterLabel();

            cluster.orders().forEach(candidate -> {
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
                }
                order.setTruckIdentifier(resolvedTruckIdentifier);
            });
        });

        orderRepository.saveAll(activeOrders);
        return new AutoAssignTruckIdentifiersResponse(listOrders(null), changes);
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

        WarehouseLocation mainWarehouse = resolveMainWarehouse();
        order.setFulfillmentWarehouse(mainWarehouse);
        return mainWarehouse;
    }

    private WarehouseLocation resolveWarehouseForOrder(Order order, Long requestedWarehouseLocationId) {
        if (requestedWarehouseLocationId != null) {
            return resolveWarehouseLocation(requestedWarehouseLocationId);
        }

        return ensureFulfillmentWarehouse(order);
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
            if (stock.getQuantity() < orderItem.getQuantity()) {
                throw new IllegalArgumentException("Nicht ausreichend Bestand im Lager "
                        + warehouseLocation.getName() + " für " + orderItem.getProduct().getName()
                        + ": benötigt " + orderItem.getQuantity()
                        + ", verfügbar " + stock.getQuantity());
            }

            stock.setQuantity(stock.getQuantity() - orderItem.getQuantity());
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
                warehouseLocation.isMainLocation()
        );
    }

    private OrderStatus determineNextStatus(OrderStatus currentStatus) {
        return switch (currentStatus) {
            case PENDING -> OrderStatus.CONFIRMED;
            case CONFIRMED -> OrderStatus.PACKED_IN_WAREHOUSE;
            case PACKED_IN_WAREHOUSE -> OrderStatus.IN_TRUCK;
            case IN_TRUCK -> OrderStatus.SHIPPED;
            case SHIPPED -> OrderStatus.DELIVERED;
            case Pending_Approval, Rejected -> null;
            case DELIVERED, CANCELLED -> null;
        };
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
        List<WarehouseOrderItemResponse> items = order.getItems().stream()
                .map(orderItem -> new WarehouseOrderItemResponse(
                        orderItem.getProduct().getId(),
                        orderItem.getProduct().getName(),
                        orderItem.getQuantity(),
                        orderItem.getProduct().getStock(),
                        stockByProductId.getOrDefault(orderItem.getProduct().getId(), 0),
                        orderItem.getProduct().getWarehousePosition()
                ))
                .toList();

        return new WarehouseOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getStatus(),
                regionKey,
                regionLabels.getOrDefault(regionKey, "Unbekannte Route"),
                order.getTruckIdentifier(),
                suggestedTruckIdentifiers.get(regionKey),
                toLocationResponse(fulfillmentWarehouse),
                warehouseWarnings.isEmpty(),
                warehouseWarnings,
                order.getShippingMethod(),
                deliverySnapshot.street(),
                deliverySnapshot.city(),
                deliverySnapshot.postalCode(),
                deliverySnapshot.country(),
                order.getCreatedAt(),
                items
        );
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
}
