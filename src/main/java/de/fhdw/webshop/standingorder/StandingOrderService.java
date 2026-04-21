package de.fhdw.webshop.standingorder;

import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.standingorder.dto.*;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StandingOrderService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.19");

    private final StandingOrderRepository repository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ProductService.DiscountLookupPort discountLookupPort;

    public List<StandingOrderResponse> listForCustomer(Long customerId) {
        return repository.findByCustomerId(customerId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public StandingOrderResponse create(User customer, CreateStandingOrderRequest request) {
        StandingOrder so = new StandingOrder();
        so.setCustomer(customer);
        so.setNextExecutionDate(request.firstExecutionDate());
        so.setActive(true);
        so.setCreatedAt(Instant.now());
        
        // Mapping der Intervall-Felder aus dem Request
        so.setIntervalType(request.intervalType());
        so.setIntervalValue(request.intervalValue());
        so.setDayOfWeek(request.dayOfWeek());
        so.setDayOfMonth(request.dayOfMonth());
        so.setMonthOfYear(request.monthOfYear());
        so.setCountBackwards(request.countBackwards());

        // WICHTIG: Sicherstellen, dass die Liste initialisiert ist
        if (so.getItems() == null) {
            so.setItems(new ArrayList<>());
        }

        if (request.items() != null) {
            for (var itemReq : request.items()) {
                Product product = productRepository.findById(itemReq.productId())
                        .orElseThrow(() -> new EntityNotFoundException("Produkt nicht gefunden: " + itemReq.productId()));
                
                StandingOrderItem item = new StandingOrderItem();
                item.setProduct(product);
                item.setQuantity(itemReq.quantity());
                item.setStandingOrder(so);
                so.getItems().add(item);
            }
        }

        return mapToResponse(repository.save(so));
    }

    @Transactional
    public StandingOrderResponse update(Long id, Long customerId, UpdateStandingOrderRequest request) {
        StandingOrder so = repository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Dauerauftrag nicht gefunden"));

        so.setIntervalType(request.intervalType());
        so.setIntervalValue(request.intervalValue());
        so.setDayOfWeek(request.dayOfWeek());
        so.setDayOfMonth(request.dayOfMonth());
        so.setMonthOfYear(request.monthOfYear());
        so.setCountBackwards(request.countBackwards());

        so.getItems().clear();
        if (request.items() != null) {
            for (var itemReq : request.items()) {
                Product product = productRepository.findById(itemReq.productId())
                        .orElseThrow(() -> new EntityNotFoundException("Produkt nicht gefunden"));
                StandingOrderItem item = new StandingOrderItem();
                item.setProduct(product);
                item.setQuantity(itemReq.quantity());
                item.setStandingOrder(so);
                so.getItems().add(item);
            }
        }

        return mapToResponse(repository.save(so));
    }

    @Transactional
    public void cancel(Long standingOrderId, Long customerId) {
        StandingOrder so = repository.findByIdAndCustomerId(standingOrderId, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Dauerauftrag nicht gefunden"));
        
        repository.delete(so); // <--- Jetzt wird er wirklich gelöscht
    }

    /**
     * US #55 — Diese Methode behebt den Fehler im StandingOrderScheduler.
     */
    @Transactional
    public void executeAllDue() {
        log.info("Suche nach fälligen Daueraufträgen...");
        List<StandingOrder> dueOrders = repository.findByActiveIsTrueAndNextExecutionDateLessThanEqual(LocalDate.now());
        for (StandingOrder so : dueOrders) {
            try {
                processSingleStandingOrder(so);
            } catch (Exception e) {
                log.error("Fehler bei Dauerauftrag {}: {}", so.getId(), e.getMessage());
            }
        }
    }

    private void processSingleStandingOrder(StandingOrder so) {
        Order order = new Order();
        order.setCustomer(so.getCustomer());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        // Wichtig: In Order.java ist shippingCost 'nullable = false'
        order.setShippingCost(BigDecimal.ZERO); 
        
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (StandingOrderItem sItem : so.getItems()) {
            OrderItem oItem = new OrderItem();
            oItem.setProduct(sItem.getProduct());
            oItem.setQuantity(sItem.getQuantity());
            
            // 1. Korrektur: getRecommendedRetailPrice() statt getPrice() (aus Product.java)
            BigDecimal price = sItem.getProduct().getRecommendedRetailPrice();
            
            // 2. Korrektur: Falls getDiscountForProduct rot bleibt, 
            // schau bitte kurz in das Interface 'ProductService.DiscountLookupPort'
            // ob die Methode dort eventuell 'getDiscount' oder ähnlich heißt.
            BigDecimal discount = discountLookupPort.findBestActiveDiscountPercent(
                so.getCustomer().getId(), 
                sItem.getProduct().getId()
            );
            BigDecimal finalPrice = applyDiscount(price, discount);
            
            // 3. Korrektur: setPriceAtOrderTime() statt setPriceAtPurchase() (aus OrderItem.java)
            oItem.setPriceAtOrderTime(finalPrice);
            oItem.setOrder(order);
            orderItems.add(oItem);
            
            total = total.add(finalPrice.multiply(BigDecimal.valueOf(sItem.getQuantity())));
        }

        order.setItems(orderItems);
        
        // 4. Korrektur: setTotalPrice() statt setTotalAmount() (aus Order.java)
        order.setTotalPrice(total);
        order.setTaxAmount(total.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP));
        
        orderRepository.save(order);

        updateNextExecutionDate(so);
        repository.save(so);
    }

    private void updateNextExecutionDate(StandingOrder so) {
        LocalDate next = so.getNextExecutionDate();
        int val = so.getIntervalValue();

        switch (so.getIntervalType()) {
            case DAYS -> next = next.plusDays(val);
            case WEEKS -> {
                // Springt X Wochen weiter und setzt den Wochentag exakt
                next = next.plusWeeks(val);
                if (so.getDayOfWeek() != null) {
                    next = next.with(java.time.DayOfWeek.of(so.getDayOfWeek()));
                }
            }
            case MONTHS -> {
                next = next.plusMonths(val);
                if (so.isCountBackwards()) {
                    // Vom Ende des Monats zurückzählen
                    next = next.withDayOfMonth(next.lengthOfMonth()).minusDays(so.getDayOfMonth() - 1);
                } else {
                    // Normaler Tag im Monat (begrenzt auf Monatslänge, z.B. 31. vs 30.)
                    next = next.withDayOfMonth(Math.min(so.getDayOfMonth(), next.lengthOfMonth()));
                }
            }
            case YEARS -> {
                next = next.plusYears(val);
                if (so.getMonthOfYear() != null && so.getDayOfMonth() != null) {
                    next = next.withMonth(so.getMonthOfYear())
                            .withDayOfMonth(Math.min(so.getDayOfMonth(), next.plusYears(0).withMonth(so.getMonthOfYear()).lengthOfMonth()));
                }
            }
        }
        so.setNextExecutionDate(next);
    }

    @Transactional
    public StandingOrderResponse toggleActive(Long id, Long customerId) {
        StandingOrder so = repository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Dauerauftrag nicht gefunden"));
        
        so.setActive(!so.isActive());
        
        // Wir speichern und erzwingen das Schreiben in die DB
        StandingOrder saved = repository.saveAndFlush(so); 
        
        return mapToResponse(saved);
    }

    private BigDecimal applyDiscount(BigDecimal price, BigDecimal discountPercent) {
        if (discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) == 0) return price;
        BigDecimal multiplier = BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal.valueOf(100)));
        return price.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private StandingOrderResponse mapToResponse(StandingOrder so) {
        List<StandingOrderItemResponse> items = so.getItems().stream()
                .map(i -> new StandingOrderItemResponse(
                        i.getId(),
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getQuantity()))
                .collect(Collectors.toList());

        return new StandingOrderResponse(
                so.getId(),
                so.getIntervalType(),
                so.getIntervalValue(),
                so.getDayOfWeek(),
                so.getDayOfMonth(),
                so.getMonthOfYear(),
                so.isCountBackwards(),
                so.getNextExecutionDate(),
                so.isActive(),
                so.getCreatedAt(),
                items
        );
    }
}