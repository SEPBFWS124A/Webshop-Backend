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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StandingOrderService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.19");
    private static final BigDecimal SHIPPING_COST = new BigDecimal("4.99");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("50.00");

    private final StandingOrderRepository standingOrderRepository;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ProductService.DiscountLookupPort discountLookupPort;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final EmailService emailService;

    public List<StandingOrderResponse> listForCustomer(Long customerId) {
        return standingOrderRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** US #51 — Create a new standing order. */
    @Transactional
    public StandingOrderResponse create(User customer, CreateStandingOrderRequest createStandingOrderRequest) {
        StandingOrder standingOrder = new StandingOrder();
        standingOrder.setCustomer(customer);
        standingOrder.setIntervalDays(createStandingOrderRequest.intervalDays());
        standingOrder.setNextExecutionDate(createStandingOrderRequest.firstExecutionDate());
        applyItems(standingOrder, createStandingOrderRequest.items());
        return toResponse(standingOrderRepository.save(standingOrder));
    }

    /** US #53 — Change interval or items of an existing standing order. */
    @Transactional
    public StandingOrderResponse update(Long standingOrderId, Long customerId,
                                        UpdateStandingOrderRequest updateStandingOrderRequest) {
        StandingOrder standingOrder = loadForCustomer(standingOrderId, customerId);
        standingOrder.setIntervalDays(updateStandingOrderRequest.intervalDays());
        if (updateStandingOrderRequest.nextExecutionDate() != null) {
            standingOrder.setNextExecutionDate(updateStandingOrderRequest.nextExecutionDate());
        }
        if (updateStandingOrderRequest.items() != null && !updateStandingOrderRequest.items().isEmpty()) {
            standingOrder.getItems().clear();
            applyItems(standingOrder, updateStandingOrderRequest.items());
        }
        standingOrder.setActive(true);
        return toResponse(standingOrderRepository.save(standingOrder));
    }

    /** US #52 — Cancel a standing order. */
    @Transactional
    public void cancel(Long standingOrderId, Long customerId) {
        StandingOrder standingOrder = loadForCustomer(standingOrderId, customerId);
        standingOrder.setActive(false);
        standingOrderRepository.save(standingOrder);
    }

    /** Called by the scheduler: execute all due standing orders and advance their next execution date. */
    @Transactional
    public void executeAllDue() {
        List<StandingOrder> dueOrders = standingOrderRepository
                .findByActiveIsTrueAndNextExecutionDateLessThanEqual(LocalDate.now());

        for (StandingOrder standingOrder : dueOrders) {
            executeStandingOrder(standingOrder);
            standingOrder.setNextExecutionDate(
                    standingOrder.getNextExecutionDate().plusDays(standingOrder.getIntervalDays()));
            standingOrderRepository.save(standingOrder);
        }
    }

    private void executeStandingOrder(StandingOrder standingOrder) {
        User customer = standingOrder.getCustomer();
        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderNumber(buildStandingOrderOrderNumber());
        order.setCustomerEmail(customer.getEmail());
        order.setCustomerName(customer.getUsername());

        deliveryAddressRepository.findFirstByUserId(customer.getId())
                .ifPresent(deliveryAddress -> applyDeliveryAddress(order, deliveryAddress));
        paymentMethodRepository.findFirstByUserId(customer.getId())
                .ifPresent(paymentMethod -> applyPaymentMethod(order, paymentMethod));

        BigDecimal subtotal = BigDecimal.ZERO;
        List<Product> updatedProducts = new ArrayList<>();
        for (StandingOrderItem standingOrderItem : standingOrder.getItems()) {
            Product product = standingOrderItem.getProduct();
            if (!product.isPurchasable() || product.getStock() <= 0) {
                continue;
            }
            int quantity = Math.min(standingOrderItem.getQuantity(), product.getStock());
            if (quantity <= 0) {
                continue;
            }
            BigDecimal discountPercent = discountLookupPort
                    .findBestActiveDiscountPercent(customer.getId(), product.getId());
            BigDecimal unitPrice = applyDiscount(product.getRecommendedRetailPrice(), discountPercent);

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
        order.setTotalPrice(subtotal.add(taxAmount).add(shippingCost).setScale(2, RoundingMode.HALF_UP));
        order.setTaxAmount(taxAmount);
        order.setShippingCost(shippingCost);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        productRepository.saveAll(updatedProducts);
        sendStandingOrderConfirmation(order, standingOrder);
    }

    private void applyItems(StandingOrder standingOrder, List<StandingOrderItemRequest> itemRequests) {
        for (StandingOrderItemRequest itemRequest : itemRequests) {
            StandingOrderItem standingOrderItem = new StandingOrderItem();
            standingOrderItem.setStandingOrder(standingOrder);
            standingOrderItem.setProduct(productService.loadProduct(itemRequest.productId()));
            standingOrderItem.setQuantity(itemRequest.quantity());
            standingOrder.getItems().add(standingOrderItem);
        }
    }

    private StandingOrder loadForCustomer(Long standingOrderId, Long customerId) {
        return standingOrderRepository.findByIdAndCustomerId(standingOrderId, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Standing order not found: " + standingOrderId));
    }

    private BigDecimal applyDiscount(BigDecimal price, BigDecimal discountPercent) {
        if (discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) == 0) {
            return price;
        }
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
                .append("Deine gespeicherte Folgebestellung wurde automatisch ausgefuehrt.\n\n")
                .append("Bestellnummer: ").append(order.getOrderNumber()).append('\n')
                .append("Intervall: alle ").append(standingOrder.getIntervalDays()).append(" Tage\n")
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
                "Folgebestellung " + order.getOrderNumber(),
                body.toString()
        );
    }

    private StandingOrderResponse toResponse(StandingOrder standingOrder) {
        List<StandingOrderItemResponse> itemResponses = standingOrder.getItems().stream()
                .map(item -> new StandingOrderItemResponse(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getQuantity()))
                .toList();
        return new StandingOrderResponse(
                standingOrder.getId(),
                standingOrder.getIntervalDays(),
                standingOrder.getNextExecutionDate(),
                standingOrder.isActive(),
                standingOrder.getCreatedAt(),
                itemResponses);
    }
}
