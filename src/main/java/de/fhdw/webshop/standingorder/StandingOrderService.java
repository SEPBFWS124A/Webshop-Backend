package de.fhdw.webshop.standingorder;

import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.standingorder.dto.*;
import de.fhdw.webshop.user.DeliveryAddress;
import de.fhdw.webshop.user.DeliveryAddressRepository;
import de.fhdw.webshop.user.PaymentMethod;
import de.fhdw.webshop.user.PaymentMethodRepository;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StandingOrderService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.19");
    private static final BigDecimal SHIPPING_COST = new BigDecimal("4.99");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("50.00");

    private final StandingOrderRepository repository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ProductService.DiscountLookupPort discountLookupPort;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final EmailService emailService;

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

        so.setIntervalType(request.intervalType());
        so.setIntervalValue(request.intervalValue());
        so.setDayOfWeek(request.dayOfWeek());
        so.setDayOfMonth(request.dayOfMonth());
        so.setMonthOfYear(request.monthOfYear());
        so.setCountBackwards(request.countBackwards());

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

        repository.delete(so);
    }

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
        User customer = so.getCustomer();
        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setOrderNumber(buildStandingOrderOrderNumber());
        order.setCustomerEmail(customer.getEmail());
        order.setCustomerName(customer.getUsername());

        deliveryAddressRepository.findFirstByUserId(customer.getId())
                .ifPresent(addr -> applyDeliveryAddress(order, addr));
        paymentMethodRepository.findFirstByUserId(customer.getId())
                .ifPresent(pm -> applyPaymentMethod(order, pm));

        BigDecimal subtotal = BigDecimal.ZERO;
        List<Product> updatedProducts = new ArrayList<>();

        for (StandingOrderItem sItem : so.getItems()) {
            Product product = sItem.getProduct();
            if (!product.isPurchasable() || product.getStock() <= 0) {
                continue;
            }
            int quantity = Math.min(sItem.getQuantity(), product.getStock());
            if (quantity <= 0) {
                continue;
            }

            BigDecimal price = product.getRecommendedRetailPrice();
            BigDecimal discount = discountLookupPort.findBestActiveDiscountPercent(
                    customer.getId(), product.getId());
            BigDecimal unitPrice = applyDiscount(price, discount);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(quantity);
            orderItem.setPriceAtOrderTime(unitPrice);
            order.getItems().add(orderItem);

            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
            product.setStock(product.getStock() - quantity);
            updatedProducts.add(product);
        }

        if (order.getItems().isEmpty()) {
            return;
        }

        BigDecimal shippingCost = calculateShippingCost(subtotal);
        BigDecimal taxAmount = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        order.setShippingCost(shippingCost);
        order.setTaxAmount(taxAmount);
        order.setTotalPrice(subtotal.add(taxAmount).add(shippingCost).setScale(2, RoundingMode.HALF_UP));

        orderRepository.save(order);
        productRepository.saveAll(updatedProducts);

        updateNextExecutionDate(so);
        repository.save(so);
        sendStandingOrderConfirmation(order, so);
    }

    private void updateNextExecutionDate(StandingOrder so) {
        LocalDate next = so.getNextExecutionDate();
        int val = so.getIntervalValue();

        switch (so.getIntervalType()) {
            case DAYS -> next = next.plusDays(val);
            case WEEKS -> {
                next = next.plusWeeks(val);
                if (so.getDayOfWeek() != null) {
                    next = next.with(java.time.DayOfWeek.of(so.getDayOfWeek()));
                }
            }
            case MONTHS -> {
                next = next.plusMonths(val);
                if (so.isCountBackwards()) {
                    next = next.withDayOfMonth(next.lengthOfMonth()).minusDays(so.getDayOfMonth() - 1);
                } else {
                    next = next.withDayOfMonth(Math.min(so.getDayOfMonth(), next.lengthOfMonth()));
                }
            }
            case YEARS -> {
                next = next.plusYears(val);
                if (so.getMonthOfYear() != null && so.getDayOfMonth() != null) {
                    next = next.withMonth(so.getMonthOfYear())
                            .withDayOfMonth(Math.min(so.getDayOfMonth(),
                                    next.withMonth(so.getMonthOfYear()).lengthOfMonth()));
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
        StandingOrder saved = repository.saveAndFlush(so);

        return mapToResponse(saved);
    }

    private BigDecimal applyDiscount(BigDecimal price, BigDecimal discountPercent) {
        if (discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) == 0) return price;
        BigDecimal multiplier = BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal.valueOf(100)));
        return price.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateShippingCost(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return BigDecimal.ZERO;
        }
        return SHIPPING_COST;
    }

    private void applyDeliveryAddress(Order order, DeliveryAddress deliveryAddress) {
        order.setDeliveryStreet(deliveryAddress.getStreet());
        order.setDeliveryCity(deliveryAddress.getCity());
        order.setDeliveryPostalCode(deliveryAddress.getPostalCode());
        order.setDeliveryCountry(deliveryAddress.getCountry());
    }

    private void applyPaymentMethod(Order order, PaymentMethod paymentMethod) {
        order.setPaymentMethodType(paymentMethod.getMethodType());
        order.setPaymentMaskedDetails(paymentMethod.getMaskedDetails());
    }

    private String buildStandingOrderOrderNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long suffix = Math.abs(System.nanoTime() % 1_000_000);
        return "STD-" + timestamp + "-" + String.format("%06d", suffix);
    }

    private void sendStandingOrderConfirmation(Order order, StandingOrder standingOrder) {
        StringBuilder body = new StringBuilder()
                .append("Dein Dauerauftrag wurde automatisch ausgefuehrt.\n\n")
                .append("Bestellnummer: ").append(order.getOrderNumber()).append('\n')
                .append("Intervall: alle ").append(standingOrder.getIntervalValue())
                .append(" ").append(standingOrder.getIntervalType().name().toLowerCase()).append("\n")
                .append("Gesamtbetrag: ").append(order.getTotalPrice()).append(" EUR\n\n")
                .append("Artikel:\n");

        for (OrderItem item : order.getItems()) {
            body.append("- ")
                    .append(item.getProduct().getName())
                    .append(" x")
                    .append(item.getQuantity())
                    .append(" = ")
                    .append(item.getPriceAtOrderTime().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .append(" EUR\n");
        }

        emailService.sendEmail(
                order.getCustomerEmail(),
                "Dauerauftrag " + order.getOrderNumber(),
                body.toString()
        );
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
                so.isNotificationsEnabled(),
                so.getCreatedAt(),
                items
        );
    }

    @Transactional
    public StandingOrderResponse toggleNotifications(Long id, Long customerId) {
        StandingOrder so = repository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Dauerauftrag nicht gefunden"));
        so.setNotificationsEnabled(!so.isNotificationsEnabled());
        return mapToResponse(repository.save(so));
    }

    public List<Product> getAllProductsForSelection() {
        return productRepository.findAll();
    }
}
