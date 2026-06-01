package de.fhdw.webshop.warehouse;

import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.order.OrderType;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.reservation.StockReservationRepository;
import de.fhdw.webshop.reservation.StockReservationStatus;
import de.fhdw.webshop.warehouse.dto.AutoBalanceResponse;
import de.fhdw.webshop.warehouse.dto.AutoBalanceTransferDetail;
import de.fhdw.webshop.warehouse.dto.BalanceStatusResponse;
import de.fhdw.webshop.warehouse.dto.ProductImbalance;
import de.fhdw.webshop.warehouse.dto.StockOverviewItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarehouseStockBalanceService {

    private static final List<OrderStatus> OPEN_TRANSFER_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PACKED_IN_WAREHOUSE,
            OrderStatus.IN_TRUCK,
            OrderStatus.SHIPPED
    );

    private final WarehouseLocationRepository warehouseLocationRepository;
    private final WarehouseProductStockRepository warehouseProductStockRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final StockReservationRepository stockReservationRepository;

    // -------------------------------------------------------------------------
    // POST /api/warehouse/stock/auto-balance
    // -------------------------------------------------------------------------

    @Transactional
    public AutoBalanceResponse autoBalance() {
        List<WarehouseLocation> warehouses = warehouseLocationRepository.findActiveLocations();
        if (warehouses.size() < 2) {
            return new AutoBalanceResponse(0, warehouses.size(), List.of());
        }

        WarehouseLocation mainWarehouse = warehouses.stream()
                .filter(WarehouseLocation::isMainLocation)
                .findFirst()
                .orElse(warehouses.getFirst());

        // Collect all products that have stock entries
        List<WarehouseProductStock> allStocks = warehouseProductStockRepository.findAll();
        Map<Long, List<WarehouseProductStock>> stockByProduct = allStocks.stream()
                .collect(Collectors.groupingBy(s -> s.getProduct().getId()));

        int warehouseCount = warehouses.size();
        List<AutoBalanceTransferDetail> transfers = new ArrayList<>();

        for (Map.Entry<Long, List<WarehouseProductStock>> entry : stockByProduct.entrySet()) {
            Long productId = entry.getKey();
            List<WarehouseProductStock> productStocks = entry.getValue();

            // Calculate reserved quantity for this product
            int reservedQuantity = stockReservationRepository.sumActiveQuantityByProductId(
                    productId, StockReservationStatus.ACTIVE, Instant.now());

            // Build stock map: warehouseId -> quantity
            Map<Long, Integer> stockMap = new LinkedHashMap<>();
            for (WarehouseLocation wh : warehouses) {
                stockMap.put(wh.getId(), 0);
            }
            for (WarehouseProductStock stock : productStocks) {
                if (stockMap.containsKey(stock.getWarehouseLocation().getId())) {
                    stockMap.put(stock.getWarehouseLocation().getId(), stock.getQuantity());
                }
            }

            int totalStock = stockMap.values().stream().mapToInt(Integer::intValue).sum();
            int availableStock = totalStock - reservedQuantity;
            if (availableStock < warehouseCount) {
                continue; // Not enough to distribute
            }

            int targetPerWarehouse = availableStock / warehouseCount;
            int remainder = availableStock % warehouseCount;

            // Target stock per warehouse (main warehouse gets the remainder)
            Map<Long, Integer> targetMap = new LinkedHashMap<>();
            for (WarehouseLocation wh : warehouses) {
                int target = targetPerWarehouse;
                if (wh.getId().equals(mainWarehouse.getId())) {
                    target += remainder;
                }
                targetMap.put(wh.getId(), target);
            }

            // Recalculate differences accounting for the reserved stock being "frozen" in place
            // We use available stock = actual stock for balancing purposes
            // Calculate surplus/deficit per warehouse
            Map<Long, Integer> surplus = new LinkedHashMap<>();
            Map<Long, Integer> deficit = new LinkedHashMap<>();

            for (WarehouseLocation wh : warehouses) {
                int actual = stockMap.getOrDefault(wh.getId(), 0);
                int target = targetMap.get(wh.getId());
                int diff = actual - target;
                if (diff > 0) {
                    surplus.put(wh.getId(), diff);
                } else if (diff < 0) {
                    deficit.put(wh.getId(), -diff);
                }
            }

            if (surplus.isEmpty() || deficit.isEmpty()) {
                continue;
            }

            // Get product reference
            Product product = productStocks.getFirst().getProduct();

            // Create transfer pairs: from surplus warehouses to deficit warehouses
            for (Map.Entry<Long, Integer> deficitEntry : deficit.entrySet()) {
                Long toWarehouseId = deficitEntry.getKey();
                int needed = deficitEntry.getValue();
                if (needed < 1) continue;

                for (Map.Entry<Long, Integer> surplusEntry : surplus.entrySet()) {
                    Long fromWarehouseId = surplusEntry.getKey();
                    int available = surplusEntry.getValue();
                    if (available < 1 || needed < 1) continue;

                    int transferQty = Math.min(available, needed);
                    if (transferQty < 1) continue;

                    // Check for existing open transfer for this pair
                    boolean existsOpenTransfer = orderRepository.existsOpenTransfer(
                            fromWarehouseId, toWarehouseId, productId, OPEN_TRANSFER_STATUSES);
                    if (existsOpenTransfer) {
                        continue;
                    }

                    // Create the internal transfer order
                    WarehouseLocation fromWarehouse = warehouses.stream()
                            .filter(wh -> wh.getId().equals(fromWarehouseId))
                            .findFirst().orElseThrow();
                    WarehouseLocation toWarehouse = warehouses.stream()
                            .filter(wh -> wh.getId().equals(toWarehouseId))
                            .findFirst().orElseThrow();

                    Order transferOrder = createInternalTransferOrder(
                            product, fromWarehouse, toWarehouse, transferQty);

                    transfers.add(new AutoBalanceTransferDetail(
                            product.getId(),
                            product.getName(),
                            fromWarehouseId,
                            fromWarehouse.getName(),
                            toWarehouseId,
                            toWarehouse.getName(),
                            transferQty,
                            transferOrder.getId(),
                            transferOrder.getOrderNumber()
                    ));

                    // Reduce remaining surplus/deficit
                    surplusEntry.setValue(available - transferQty);
                    needed -= transferQty;
                }
                deficitEntry.setValue(needed);
            }
        }

        return new AutoBalanceResponse(transfers.size(), warehouseCount, transfers);
    }

    // -------------------------------------------------------------------------
    // GET /api/warehouse/stock/balance-status
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public BalanceStatusResponse getBalanceStatus() {
        List<WarehouseLocation> warehouses = warehouseLocationRepository.findActiveLocations();
        if (warehouses.size() < 2) {
            return new BalanceStatusResponse(true, warehouses.size(), List.of());
        }

        WarehouseLocation mainWarehouse = warehouses.stream()
                .filter(WarehouseLocation::isMainLocation)
                .findFirst()
                .orElse(warehouses.getFirst());

        int warehouseCount = warehouses.size();
        List<WarehouseProductStock> allStocks = warehouseProductStockRepository.findAll();
        Map<Long, List<WarehouseProductStock>> stockByProduct = allStocks.stream()
                .collect(Collectors.groupingBy(s -> s.getProduct().getId()));

        List<ProductImbalance> imbalances = new ArrayList<>();

        for (Map.Entry<Long, List<WarehouseProductStock>> entry : stockByProduct.entrySet()) {
            Long productId = entry.getKey();
            List<WarehouseProductStock> productStocks = entry.getValue();

            Map<Long, Integer> stockMap = new LinkedHashMap<>();
            for (WarehouseLocation wh : warehouses) {
                stockMap.put(wh.getId(), 0);
            }
            for (WarehouseProductStock stock : productStocks) {
                if (stockMap.containsKey(stock.getWarehouseLocation().getId())) {
                    stockMap.put(stock.getWarehouseLocation().getId(), stock.getQuantity());
                }
            }

            int totalStock = stockMap.values().stream().mapToInt(Integer::intValue).sum();
            if (totalStock < warehouseCount) {
                continue;
            }

            int targetPerWarehouse = totalStock / warehouseCount;
            int maxDeviation = stockMap.values().stream()
                    .mapToInt(actual -> Math.abs(actual - targetPerWarehouse))
                    .max().orElse(0);

            // Consider balanced if maxDeviation <= 1
            if (maxDeviation > 1) {
                Product product = productStocks.getFirst().getProduct();
                imbalances.add(new ProductImbalance(
                        productId,
                        product.getName(),
                        stockMap,
                        targetPerWarehouse,
                        maxDeviation
                ));
            }
        }

        return new BalanceStatusResponse(imbalances.isEmpty(), warehouseCount, imbalances);
    }

    // -------------------------------------------------------------------------
    // GET /api/warehouse/stock-overview
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<StockOverviewItem> getStockOverview() {
        List<Product> products = productRepository.findAll().stream()
                .filter(p -> p.getParentProduct() == null)
                .toList();

        List<WarehouseLocation> warehouses = warehouseLocationRepository.findActiveLocations();
        List<WarehouseProductStock> allStocks = warehouseProductStockRepository.findAll();
        Map<Long, List<WarehouseProductStock>> stockByProduct = allStocks.stream()
                .collect(Collectors.groupingBy(s -> s.getProduct().getId()));

        List<StockOverviewItem> result = new ArrayList<>();

        for (Product product : products) {
            List<WarehouseProductStock> productStocks = stockByProduct.getOrDefault(product.getId(), List.of());

            Map<Long, Integer> stockByLocation = new LinkedHashMap<>();
            for (WarehouseLocation wh : warehouses) {
                stockByLocation.put(wh.getId(), 0);
            }
            for (WarehouseProductStock stock : productStocks) {
                if (stockByLocation.containsKey(stock.getWarehouseLocation().getId())) {
                    stockByLocation.put(stock.getWarehouseLocation().getId(), stock.getQuantity());
                }
            }

            int totalStock = stockByLocation.values().stream().mapToInt(Integer::intValue).sum();
            int reservedStock = stockReservationRepository.sumActiveQuantityByProductId(
                    product.getId(), StockReservationStatus.ACTIVE, Instant.now());
            int availableStock = Math.max(0, totalStock - reservedStock);

            // Find next expiring reservation
            Instant nextExpiration = null;
            // We get this from the reservation repo (the first active one sorted by expiration)
            // simplified: use null for now, we'll query it

            result.add(new StockOverviewItem(
                    product.getId(),
                    product.getName(),
                    product.getSku(),
                    totalStock,
                    reservedStock,
                    availableStock,
                    stockByLocation,
                    nextExpiration
            ));
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Internal transfer order upon DELIVERED status
    // -------------------------------------------------------------------------

    @Transactional
    public void handleInternalTransferDelivered(Order order) {
        if (!order.isInternalTransfer()) {
            return;
        }
        WarehouseLocation targetWarehouse = order.getFulfillmentWarehouse();
        if (targetWarehouse == null) {
            return;
        }

        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            int quantity = item.getQuantity();

            // Increase stock at target warehouse
            WarehouseProductStock targetStock = warehouseProductStockRepository
                    .findByProductIdAndWarehouseLocationId(product.getId(), targetWarehouse.getId())
                    .orElseGet(() -> {
                        WarehouseProductStock s = new WarehouseProductStock();
                        s.setProduct(product);
                        s.setWarehouseLocation(targetWarehouse);
                        s.setQuantity(0);
                        return s;
                    });
            targetStock.setQuantity(targetStock.getQuantity() + quantity);
            warehouseProductStockRepository.save(targetStock);

            // Decrease stock at source warehouse
            WarehouseLocation sourceWarehouse = order.getSourceWarehouse();
            if (sourceWarehouse != null) {
                WarehouseProductStock sourceStock = warehouseProductStockRepository
                        .findByProductIdAndWarehouseLocationId(product.getId(), sourceWarehouse.getId())
                        .orElse(null);
                if (sourceStock != null) {
                    sourceStock.setQuantity(Math.max(0, sourceStock.getQuantity() - quantity));
                    warehouseProductStockRepository.save(sourceStock);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Order createInternalTransferOrder(
            Product product,
            WarehouseLocation fromWarehouse,
            WarehouseLocation toWarehouse,
            int quantity
    ) {
        String orderNumber = buildUniqueInternalTransferOrderNumber(product.getId());

        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setOrderType(OrderType.INTERNAL_TRANSFER);
        order.setInternalTransfer(true);
        order.setSourceWarehouse(fromWarehouse);
        order.setFulfillmentWarehouse(fromWarehouse); // fulfillment = the warehouse that ships
        order.setStatus(OrderStatus.CONFIRMED);
        order.setCustomerName("Interner Transfer");
        order.setCustomerEmail("warehouse@intern.fhdw.de");
        order.setTotalPrice(BigDecimal.ZERO);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setShippingCost(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);

        // Delivery address = target warehouse address
        order.setDeliveryStreet(toWarehouse.getStreet());
        order.setDeliveryCity(toWarehouse.getCity());
        order.setDeliveryPostalCode(toWarehouse.getPostalCode());
        order.setDeliveryCountry(toWarehouse.getCountry());

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setPriceAtOrderTime(BigDecimal.ZERO);
        order.getItems().add(item);

        return orderRepository.save(order);
    }

    private String buildUniqueInternalTransferOrderNumber(Long productId) {
        long baseTimestamp = Instant.now().getEpochSecond();
        for (int offset = 0; offset < 120; offset++) {
            String candidate = "INT-TRANSFER-" + (baseTimestamp + offset) + "-" + productId;
            if (!orderRepository.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Could not generate a unique internal transfer order number");
    }
}
