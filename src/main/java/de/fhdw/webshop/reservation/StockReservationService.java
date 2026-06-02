package de.fhdw.webshop.reservation;

import de.fhdw.webshop.cart.CartItem;
import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.product.ProductType;
import de.fhdw.webshop.reservation.dto.AvailabilityNotificationResponse;
import de.fhdw.webshop.reservation.dto.CartReservationInfo;
import de.fhdw.webshop.reservation.dto.InventoryStockResponse;
import de.fhdw.webshop.reservation.dto.StockReservationResponse;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.warehouse.WarehouseLocation;
import de.fhdw.webshop.warehouse.WarehouseLocationRepository;
import de.fhdw.webshop.warehouse.WarehouseProductStock;
import de.fhdw.webshop.warehouse.WarehouseProductStockRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockReservationService {

    private static final int VIRTUAL_GIFT_CARD_STOCK = 999999;

    private final StockReservationRepository stockReservationRepository;
    private final AvailabilityNotificationRepository availabilityNotificationRepository;
    private final ProductRepository productRepository;
    private final WarehouseLocationRepository warehouseLocationRepository;
    private final WarehouseProductStockRepository warehouseProductStockRepository;
    private final EmailService emailService;

    @Value("${app.stock-reservations.ttl-minutes:15}")
    private long reservationTtlMinutes;

    @Transactional
    public int expireOverdueReservations() {
        Instant now = Instant.now();
        List<StockReservation> overdueReservations = stockReservationRepository
                .findByStatusAndReservedUntilBefore(StockReservationStatus.ACTIVE, now);

        overdueReservations.forEach(reservation -> {
            reservation.setStatus(StockReservationStatus.EXPIRED);
            reservation.setUpdatedAt(now);
        });
        stockReservationRepository.saveAll(overdueReservations);
        return overdueReservations.size();
    }

    @Transactional
    public CartReservationInfo refreshReservation(CartItem cartItem) {
        expireOverdueReservations();

        if (cartItem == null || cartItem.getId() == null || cartItem.getQuantity() <= 0
                || !requiresReservation(cartItem.getProduct())) {
            return emptyReservationInfo();
        }

        Instant now = Instant.now();
        StockReservation reservation = stockReservationRepository
                .findByCartItemIdAndStatus(cartItem.getId(), StockReservationStatus.ACTIVE)
                .orElseGet(() -> {
                    StockReservation newReservation = new StockReservation();
                    newReservation.setCartItemId(cartItem.getId());
                    newReservation.setUser(cartItem.getUser());
                    newReservation.setProduct(cartItem.getProduct());
                    newReservation.setCreatedAt(now);
                    return newReservation;
                });

        reservation.setUser(cartItem.getUser());
        reservation.setProduct(cartItem.getProduct());
        reservation.setQuantity(cartItem.getQuantity());
        reservation.setReservedUntil(now.plus(Duration.ofMinutes(reservationTtlMinutes)));
        reservation.setStatus(StockReservationStatus.ACTIVE);
        reservation.setUpdatedAt(now);

        StockReservation savedReservation = stockReservationRepository.save(reservation);
        return toCartInfo(savedReservation);
    }

    @Transactional(readOnly = true)
    public CartReservationInfo getCartReservationInfo(CartItem cartItem) {
        if (cartItem == null || cartItem.getId() == null || !requiresReservation(cartItem.getProduct())) {
            return emptyReservationInfo();
        }

        return stockReservationRepository
                .findByCartItemIdAndStatus(cartItem.getId(), StockReservationStatus.ACTIVE)
                .filter(reservation -> reservation.getReservedUntil().isAfter(Instant.now()))
                .map(this::toCartInfo)
                .orElseGet(() -> new CartReservationInfo(0, null, 0, "EXPIRED"));
    }

    @Transactional
    public void releaseCartItemReservation(Long cartItemId) {
        if (cartItemId == null) {
            return;
        }
        stockReservationRepository
                .findByCartItemIdAndStatus(cartItemId, StockReservationStatus.ACTIVE)
                .ifPresent(reservation -> {
                    reservation.setStatus(StockReservationStatus.RELEASED);
                    reservation.setUpdatedAt(Instant.now());
                    stockReservationRepository.save(reservation);
                });
    }

    @Transactional
    public void releaseUserReservations(Long userId) {
        if (userId == null) {
            return;
        }
        Instant now = Instant.now();
        List<StockReservation> reservations = stockReservationRepository
                .findByUserIdAndStatus(userId, StockReservationStatus.ACTIVE);
        reservations.forEach(reservation -> {
            reservation.setStatus(StockReservationStatus.RELEASED);
            reservation.setUpdatedAt(now);
        });
        stockReservationRepository.saveAll(reservations);
    }

    @Transactional
    public void consumeUserReservations(Long userId) {
        if (userId == null) {
            return;
        }
        Instant now = Instant.now();
        List<StockReservation> reservations = stockReservationRepository
                .findByUserIdAndStatus(userId, StockReservationStatus.ACTIVE);
        reservations.forEach(reservation -> {
            reservation.setStatus(StockReservationStatus.CONSUMED);
            reservation.setUpdatedAt(now);
        });
        stockReservationRepository.saveAll(reservations);
    }

    @Transactional(readOnly = true)
    public boolean hasValidReservation(Long cartItemId, Long productId, int quantity) {
        if (cartItemId == null) {
            return true;
        }
        return stockReservationRepository
                .findByCartItemIdAndStatus(cartItemId, StockReservationStatus.ACTIVE)
                .filter(reservation -> productId.equals(reservation.getProduct().getId()))
                .filter(reservation -> reservation.getQuantity() >= quantity)
                .filter(reservation -> reservation.getReservedUntil().isAfter(Instant.now()))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public int getReservedQuantity(Product product) {
        if (!requiresReservation(product)) {
            return 0;
        }
        return stockReservationRepository.sumActiveQuantityByProductId(
                product.getId(),
                StockReservationStatus.ACTIVE,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public int getReservedQuantityExcludingCartItem(Product product, Long cartItemId) {
        if (!requiresReservation(product)) {
            return 0;
        }
        return stockReservationRepository.sumActiveQuantityByProductIdExcludingCartItem(
                product.getId(),
                cartItemId,
                StockReservationStatus.ACTIVE,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public int getAvailableQuantity(Product product) {
        return getReservableQuantity(product, null);
    }

    @Transactional(readOnly = true)
    public int getReservableQuantity(Product product, Long cartItemId) {
        if (!product.isPurchasable()) {
            return 0;
        }
        if (product.getProductType() == ProductType.DIGITAL_GIFT_CARD) {
            return VIRTUAL_GIFT_CARD_STOCK;
        }
        int reservedByOthers = getReservedQuantityExcludingCartItem(product, cartItemId);
        return Math.max(product.getStock() - reservedByOthers, 0);
    }

    @Transactional(readOnly = true)
    public List<StockReservationResponse> listActiveReservations() {
        Instant now = Instant.now();
        return stockReservationRepository.findByStatusOrderByReservedUntilAsc(StockReservationStatus.ACTIVE)
                .stream()
                .map(reservation -> toResponse(reservation, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryStockResponse> listInventoryStock() {
        Instant now = Instant.now();
        List<StockReservation> activeReservations = stockReservationRepository
                .findByStatusOrderByReservedUntilAsc(StockReservationStatus.ACTIVE)
                .stream()
                .filter(reservation -> reservation.getReservedUntil().isAfter(now))
                .toList();
        Map<Long, List<StockReservation>> reservationsByProduct = activeReservations.stream()
                .collect(Collectors.groupingBy(reservation -> reservation.getProduct().getId()));

        List<WarehouseLocation> locations = warehouseLocationRepository.findActiveLocations();
        List<WarehouseProductStock> allWarehouseStocks = warehouseProductStockRepository.findAll();
        Map<Long, List<WarehouseProductStock>> warehouseStocksByProduct = allWarehouseStocks.stream()
                .collect(Collectors.groupingBy(stock -> stock.getProduct().getId()));

        return productRepository.findAll().stream()
                .filter(product -> product.getParentProduct() == null)
                .sorted(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER))
                .map(product -> {
                    List<StockReservation> productReservations = reservationsByProduct
                            .getOrDefault(product.getId(), List.of());
                    int reservedStock = productReservations.stream()
                            .mapToInt(StockReservation::getQuantity)
                            .sum();
                    Instant nextExpiry = productReservations.stream()
                            .map(StockReservation::getReservedUntil)
                            .min(Instant::compareTo)
                            .orElse(null);

                    Map<Long, Integer> stockByLocation = new LinkedHashMap<>();
                    locations.forEach(location -> stockByLocation.put(location.getId(), 0));
                    warehouseStocksByProduct.getOrDefault(product.getId(), List.of())
                            .forEach(stock -> {
                                Long locationId = stock.getWarehouseLocation().getId();
                                if (stockByLocation.containsKey(locationId)) {
                                    stockByLocation.put(locationId, stock.getQuantity());
                                }
                            });
                    int totalStock = stockByLocation.values().stream().mapToInt(Integer::intValue).sum();

                    return new InventoryStockResponse(
                            product.getId(),
                            product.getName(),
                            product.getSku(),
                            product.getCategory(),
                            totalStock,
                            reservedStock,
                            product.getProductType() == ProductType.DIGITAL_GIFT_CARD
                                    ? VIRTUAL_GIFT_CARD_STOCK
                                    : Math.max(totalStock - reservedStock, 0),
                            stockByLocation,
                            nextExpiry
                    );
                })
                .toList();
    }

    @Transactional
    public AvailabilityNotificationResponse requestAvailabilityNotification(User user, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));
        AvailabilityNotification notification = availabilityNotificationRepository
                .findByUserIdAndProductId(user.getId(), productId)
                .orElseGet(() -> {
                    AvailabilityNotification newNotification = new AvailabilityNotification();
                    newNotification.setUser(user);
                    newNotification.setProduct(product);
                    return newNotification;
                });

        notification.setActive(true);
        notification.setNotifiedAt(null);
        AvailabilityNotification savedNotification = availabilityNotificationRepository.save(notification);
        return toResponse(savedNotification);
    }

    @Transactional
    public void sendAvailabilityNotifications() {
        for (AvailabilityNotification notification : availabilityNotificationRepository.findByActiveTrue()) {
            Product product = notification.getProduct();
            if (getAvailableQuantity(product) <= 0) {
                continue;
            }

            boolean sent = emailService.sendEmail(
                    notification.getUser().getEmail(),
                    "Artikel wieder verfügbar: " + product.getName(),
                    "Der Artikel \"" + product.getName() + "\" ist wieder verfügbar. "
                            + "Du kannst ihn jetzt im Webshop bestellen."
            );
            if (sent) {
                notification.setActive(false);
                notification.setNotifiedAt(Instant.now());
                availabilityNotificationRepository.save(notification);
            }
        }
    }

    private CartReservationInfo toCartInfo(StockReservation reservation) {
        long secondsRemaining = Math.max(Duration.between(Instant.now(), reservation.getReservedUntil()).toSeconds(), 0);
        return new CartReservationInfo(
                reservation.getQuantity(),
                reservation.getReservedUntil(),
                secondsRemaining,
                reservation.getStatus().name()
        );
    }

    private CartReservationInfo emptyReservationInfo() {
        return new CartReservationInfo(0, null, 0, null);
    }

    private StockReservationResponse toResponse(StockReservation reservation, Instant now) {
        User user = reservation.getUser();
        Product product = reservation.getProduct();
        return new StockReservationResponse(
                reservation.getId(),
                user.getId(),
                user.getUsername(),
                user.getCustomerNumber(),
                product.getId(),
                product.getName(),
                product.getSku(),
                reservation.getCartItemId(),
                reservation.getQuantity(),
                reservation.getStatus().name(),
                reservation.getReservedUntil(),
                Math.max(Duration.between(now, reservation.getReservedUntil()).toSeconds(), 0),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }

    private AvailabilityNotificationResponse toResponse(AvailabilityNotification notification) {
        Product product = notification.getProduct();
        return new AvailabilityNotificationResponse(
                product.getId(),
                product.getName(),
                notification.isActive(),
                notification.getCreatedAt(),
                notification.getNotifiedAt()
        );
    }

    private boolean requiresReservation(Product product) {
        return product != null && product.getProductType() != ProductType.DIGITAL_GIFT_CARD;
    }
}
