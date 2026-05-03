package de.fhdw.webshop.order;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.address.AddressLookupService;
import de.fhdw.webshop.address.AddressValidationRequest;
import de.fhdw.webshop.address.AddressValidationResponse;
import de.fhdw.webshop.cart.CartItem;
import de.fhdw.webshop.cart.CartService;
import de.fhdw.webshop.cart.CartRepository;
import de.fhdw.webshop.discount.Coupon;
import de.fhdw.webshop.discount.CouponRepository;
import de.fhdw.webshop.order.dto.OrderItemResponse;
import de.fhdw.webshop.order.dto.OrderPreviewItemResponse;
import de.fhdw.webshop.order.dto.OrderPreviewResponse;
import de.fhdw.webshop.order.dto.OrderResponse;
import de.fhdw.webshop.order.dto.PlaceOrderRequest;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.user.DeliveryAddress;
import de.fhdw.webshop.user.DeliveryAddressRepository;
import de.fhdw.webshop.user.PaymentMethod;
import de.fhdw.webshop.user.PaymentMethodRepository;
import de.fhdw.webshop.user.PaymentMethodSupport;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserService;
import de.fhdw.webshop.user.dto.DeliveryAddressRequest;
import de.fhdw.webshop.user.dto.PaymentMethodRequest;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.fhdw.webshop.notification.EmailService;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.19");
    private static final BigDecimal SHIPPING_COST = new BigDecimal("4.99");
    private static final BigDecimal EXPRESS_SHIPPING_COST = new BigDecimal("9.99");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("50.00");
    private static final BigDecimal CARBON_COMPENSATION_RATE_PER_KG = new BigDecimal("0.05");
    private static final Duration STANDARD_TOTAL_DELIVERY = Duration.ofDays(4);
    private static final Duration EXPRESS_TOTAL_DELIVERY = Duration.ofDays(2);
    private static final Duration STANDARD_MIN_REMAINING_EARLY = Duration.ofDays(3);
    private static final Duration EXPRESS_MIN_REMAINING_EARLY = Duration.ofHours(36);
    private static final Duration STANDARD_MIN_REMAINING_PACKED = Duration.ofDays(2);
    private static final Duration EXPRESS_MIN_REMAINING_PACKED = Duration.ofHours(24);
    private static final Duration STANDARD_MIN_REMAINING_TRUCK = Duration.ofHours(30);
    private static final Duration EXPRESS_MIN_REMAINING_TRUCK = Duration.ofHours(12);
    private static final Duration STANDARD_MIN_REMAINING_SHIPPED = Duration.ofHours(18);
    private static final Duration EXPRESS_MIN_REMAINING_SHIPPED = Duration.ofHours(6);

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartService cartService;
    private final CouponRepository couponRepository;
    private final ProductService.DiscountLookupPort discountLookupPort;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final AddressLookupService addressLookupService;

    public List<OrderResponse> listOrdersForCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public long countOrdersForCustomer(Long customerId) {
        return orderRepository.countByCustomerId(customerId);
    }

    public Instant findLatestOrderTimestamp(Long customerId) {
        return orderRepository.findFirstByCustomerIdOrderByCreatedAtDesc(customerId)
                .map(Order::getCreatedAt)
                .orElse(null);
    }

    public OrderResponse getOrder(Long orderId, Long customerId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        return toResponse(order);
    }

    /** US #42 - Convert the current cart into a confirmed order. Coupon reduces the order subtotal. */
    @Transactional(readOnly = true)
    public OrderPreviewResponse previewOrder(User customer, PlaceOrderRequest placeOrderRequest) {
        PreparedOrder preparedOrder = prepareCustomerOrder(customer, placeOrderRequest);
        return toPreviewResponse(preparedOrder);
    }

    /** US #42 - Convert the current cart into a confirmed order. Coupon reduces the order subtotal. */
    @Transactional
    public OrderResponse placeOrder(User customer, PlaceOrderRequest placeOrderRequest) {
        validateLegalAcceptance(placeOrderRequest);
        PreparedOrder preparedOrder = prepareCustomerOrder(customer, placeOrderRequest);
        DeliveryAddressRequest deliveryAddressRequest = placeOrderRequest != null ? placeOrderRequest.deliveryAddress() : null;
        PaymentMethodRequest paymentMethodRequest = placeOrderRequest != null ? placeOrderRequest.paymentMethod() : null;
        if (placeOrderRequest != null && Boolean.TRUE.equals(placeOrderRequest.saveDeliveryAddress()) && deliveryAddressRequest != null) {
            userService.saveDeliveryAddress(customer, deliveryAddressRequest);
        }
        if (placeOrderRequest != null && Boolean.TRUE.equals(placeOrderRequest.savePaymentMethod()) && paymentMethodRequest != null) {
            userService.savePaymentMethod(customer, paymentMethodRequest);
        }
        Order savedOrder = persistPreparedOrder(preparedOrder);
        cartService.clearCartSilently(customer.getId());
        markCouponAsUsed(preparedOrder.coupon(), savedOrder, customer);
        boolean confirmationEmailSent = sendOrderConfirmation(savedOrder);
        return toResponse(savedOrder, confirmationEmailSent);
    }

    /** US #78 - Guests can place orders with explicit checkout data. */
    @Transactional(readOnly = true)
    public OrderPreviewResponse previewGuestOrder(@Valid PlaceOrderRequest placeOrderRequest) {
        PreparedOrder preparedOrder = prepareGuestOrder(placeOrderRequest);
        return toPreviewResponse(preparedOrder);
    }

    /** US #78 - Guests can place orders with explicit checkout data. */
    @Transactional
    public OrderResponse placeGuestOrder(@Valid PlaceOrderRequest placeOrderRequest) {
        validateLegalAcceptance(placeOrderRequest);
        PreparedOrder preparedOrder = prepareGuestOrder(placeOrderRequest);
        Order savedOrder = persistPreparedOrder(preparedOrder);
        boolean confirmationEmailSent = sendOrderConfirmation(savedOrder);
        return toResponse(savedOrder, confirmationEmailSent);
    }

    private PreparedOrder prepareCustomerOrder(User customer, PlaceOrderRequest placeOrderRequest) {
        List<CartItem> cartItems = cartRepository.findByUserId(customer.getId());
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cannot place an order with an empty cart");
        }
        DeliveryAddressRequest deliveryAddressRequest = placeOrderRequest != null ? placeOrderRequest.deliveryAddress() : null;
        PaymentMethodRequest paymentMethodRequest = placeOrderRequest != null ? placeOrderRequest.paymentMethod() : null;
        ShippingMethod shippingMethod = resolveShippingMethod(placeOrderRequest);
        DeliveryAddressSnapshot deliveryAddress = resolveDeliveryAddress(customer, deliveryAddressRequest);
        PaymentMethodSnapshot paymentMethod = resolvePaymentMethod(customer, paymentMethodRequest);
        String couponCode = placeOrderRequest != null ? placeOrderRequest.couponCode() : null;
        Coupon coupon = resolveCoupon(couponCode, customer);
        String orderNumber = placeOrderRequest != null ? placeOrderRequest.previewOrderNumber() : null;
        boolean carbonCompensationSelected = placeOrderRequest != null && Boolean.TRUE.equals(placeOrderRequest.carbonCompensationSelected());
        String confirmationEmail = placeOrderRequest != null && placeOrderRequest.email() != null && !placeOrderRequest.email().isBlank()
                ? placeOrderRequest.email().trim()
                : customer.getEmail();
        String displayName = formatCustomerName(
                placeOrderRequest != null ? placeOrderRequest.customerSalutation() : null,
                placeOrderRequest != null ? placeOrderRequest.customerName() : null,
                customer.getUsername()
        );
        return prepareOrder(
                orderNumber,
                customer,
                confirmationEmail,
                displayName,
                cartItems.stream()
                        .map(cartItem -> new RequestedOrderItem(cartItem.getProduct(), cartItem.getQuantity()))
                        .toList(),
                deliveryAddress,
                shippingMethod,
                paymentMethod,
                coupon,
                customer.getId(),
                placeOrderRequest != null && Boolean.TRUE.equals(placeOrderRequest.allowUnverifiedAddress()),
                carbonCompensationSelected
        );
    }

    private PreparedOrder prepareGuestOrder(PlaceOrderRequest placeOrderRequest) {
        if (placeOrderRequest == null || placeOrderRequest.items() == null || placeOrderRequest.items().isEmpty()) {
            throw new IllegalArgumentException("Guest orders require at least one item");
        }
        if (placeOrderRequest.email() == null || placeOrderRequest.email().isBlank()) {
            throw new IllegalArgumentException("Email is required for guest checkout");
        }
        if (placeOrderRequest.deliveryAddress() == null) {
            throw new IllegalArgumentException("Delivery address is required");
        }
        if (placeOrderRequest.paymentMethod() == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
        if (placeOrderRequest.couponCode() != null && !placeOrderRequest.couponCode().isBlank()) {
            throw new IllegalArgumentException("Coupons are only available for logged-in customers");
        }

        List<RequestedOrderItem> requestedItems = placeOrderRequest.items().stream()
                .map(item -> new RequestedOrderItem(productService.loadProduct(item.productId()), item.quantity()))
                .toList();

        return prepareOrder(
                placeOrderRequest.previewOrderNumber(),
                null,
                placeOrderRequest.email().trim(),
                formatCustomerName(placeOrderRequest.customerSalutation(), placeOrderRequest.customerName(), placeOrderRequest.email()),
                requestedItems,
                new DeliveryAddressSnapshot(
                        placeOrderRequest.deliveryAddress().street(),
                        placeOrderRequest.deliveryAddress().city(),
                        placeOrderRequest.deliveryAddress().postalCode(),
                        placeOrderRequest.deliveryAddress().country()
                ),
                resolveShippingMethod(placeOrderRequest),
                resolveGuestPaymentMethod(placeOrderRequest.paymentMethod()),
                null,
                null,
                Boolean.TRUE.equals(placeOrderRequest.allowUnverifiedAddress()),
                Boolean.TRUE.equals(placeOrderRequest.carbonCompensationSelected())
        );
    }

    private PreparedOrder prepareOrder(String requestedOrderNumber,
                                       User customer,
                                       String customerEmail,
                                       String customerName,
                                       List<RequestedOrderItem> requestedItems,
                                       DeliveryAddressSnapshot deliveryAddress,
                                       ShippingMethod shippingMethod,
                                       PaymentMethodSnapshot paymentMethod,
                                       Coupon coupon,
                                       Long discountCustomerId,
                                       boolean allowUnverifiedAddress,
                                       boolean carbonCompensationSelected) {
        Order order = new Order();
        DeliveryAddressSnapshot validatedDeliveryAddress = validateDeliveryAddress(deliveryAddress, allowUnverifiedAddress);
        order.setCustomer(customer);
        order.setOrderNumber(resolveOrderNumber(requestedOrderNumber));
        order.setCustomerEmail(customerEmail);
        order.setCustomerName(
                customerName != null && !customerName.isBlank()
                        ? customerName.trim()
                        : (customer != null ? customer.getUsername() : customerEmail)
        );
        order.setDeliveryStreet(validatedDeliveryAddress.street());
        order.setDeliveryCity(validatedDeliveryAddress.city());
        order.setDeliveryPostalCode(validatedDeliveryAddress.postalCode());
        order.setDeliveryCountry(validatedDeliveryAddress.country());
        order.setShippingMethod(shippingMethod);
        order.setPaymentMethodType(paymentMethod.methodType());
        order.setPaymentMaskedDetails(paymentMethod.maskedDetails());
        order.setCouponCode(coupon != null ? coupon.getCode() : null);

        BigDecimal itemSubtotal = BigDecimal.ZERO;
        BigDecimal totalCo2EmissionKg = BigDecimal.ZERO;
        List<PreparedOrderItem> preparedItems = new ArrayList<>();
        for (RequestedOrderItem requestedItem : requestedItems) {
            Product product = requestedItem.product();
            if (!product.isPurchasable() || product.getStock() <= 0) {
                throw new IllegalArgumentException(product.getName() + " is no longer available");
            }
            if (requestedItem.quantity() > product.getStock()) {
                throw new IllegalArgumentException("Only " + product.getStock() + " units of " + product.getName() + " are available");
            }

            BigDecimal discountPercent = discountCustomerId != null
                    ? discountLookupPort.findBestActiveDiscountPercent(discountCustomerId, product.getId())
                    : BigDecimal.ZERO;
            BigDecimal unitPrice = applyDiscount(product.getRecommendedRetailPrice(), discountPercent);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(requestedItem.quantity())).setScale(2, RoundingMode.HALF_UP);
            itemSubtotal = itemSubtotal.add(lineTotal);
            if (product.getCo2EmissionKg() != null) {
                totalCo2EmissionKg = totalCo2EmissionKg.add(
                        product.getCo2EmissionKg().multiply(BigDecimal.valueOf(requestedItem.quantity())));
            }
            preparedItems.add(new PreparedOrderItem(product, requestedItem.quantity(), unitPrice, lineTotal));
        }
        totalCo2EmissionKg = totalCo2EmissionKg.setScale(3, RoundingMode.HALF_UP);

        BigDecimal discountAmount = calculateCouponDiscount(itemSubtotal, coupon);
        BigDecimal subtotal = itemSubtotal.subtract(discountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shippingCost = calculateShippingCost(subtotal, shippingMethod);
        BigDecimal taxAmount = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal climateContributionAmount = calculateClimateContributionAmount(
                totalCo2EmissionKg,
                carbonCompensationSelected
        );
        BigDecimal totalPrice = subtotal.add(taxAmount).add(shippingCost).add(climateContributionAmount).setScale(2, RoundingMode.HALF_UP);

        return new PreparedOrder(
                order,
                preparedItems,
                coupon,
                discountAmount,
                taxAmount,
                subtotal,
                shippingCost,
                climateContributionAmount,
                totalCo2EmissionKg,
                carbonCompensationSelected,
                totalPrice
        );
    }

    private DeliveryAddressSnapshot validateDeliveryAddress(DeliveryAddressSnapshot deliveryAddress, boolean allowUnverifiedAddress) {
        AddressValidationResponse validationResponse = addressLookupService.validateAddress(new AddressValidationRequest(
                deliveryAddress.street(),
                deliveryAddress.city(),
                deliveryAddress.postalCode(),
                deliveryAddress.country()
        ));

        if (!validationResponse.valid()) {
            if (allowUnverifiedAddress) {
                return deliveryAddress;
            }
            throw new IllegalArgumentException(validationResponse.message() != null
                    ? validationResponse.message()
                    : "Delivery address could not be validated");
        }

        return new DeliveryAddressSnapshot(
                validationResponse.street() != null ? validationResponse.street() : deliveryAddress.street(),
                validationResponse.city() != null ? validationResponse.city() : deliveryAddress.city(),
                validationResponse.postalCode() != null ? validationResponse.postalCode() : deliveryAddress.postalCode(),
                validationResponse.country() != null ? validationResponse.country() : deliveryAddress.country()
        );
    }

    private String formatCustomerName(String salutation, String customerName, String fallback) {
        String normalizedName = customerName != null && !customerName.isBlank()
                ? customerName.trim()
                : (fallback != null ? fallback.trim() : "");
        String normalizedSalutation = salutation != null ? salutation.trim() : "";

        if (normalizedSalutation.isBlank() || "Keine Angabe".equalsIgnoreCase(normalizedSalutation)) {
            return normalizedName;
        }

        if (normalizedName.isBlank()) {
            return normalizedSalutation;
        }

        return normalizedSalutation + " " + normalizedName;
    }

    private ShippingMethod resolveShippingMethod(PlaceOrderRequest placeOrderRequest) {
        if (placeOrderRequest == null || placeOrderRequest.shippingMethod() == null) {
            return ShippingMethod.STANDARD;
        }
        return placeOrderRequest.shippingMethod();
    }

    private void validateLegalAcceptance(PlaceOrderRequest placeOrderRequest) {
        if (placeOrderRequest == null
                || !Boolean.TRUE.equals(placeOrderRequest.acceptedTermsAndConditions())
                || !Boolean.TRUE.equals(placeOrderRequest.acceptedPrivacyPolicy())) {
            throw new IllegalArgumentException("Bitte akzeptiere AGB, Widerrufsbelehrung und Datenschutzhinweise vor der Bestellung");
        }
    }

    private BigDecimal calculateShippingCost(BigDecimal subtotal, ShippingMethod shippingMethod) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        if (shippingMethod == ShippingMethod.EXPRESS) {
            return EXPRESS_SHIPPING_COST;
        }

        if (subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return BigDecimal.ZERO;
        }

        return SHIPPING_COST;
    }

    private Order persistPreparedOrder(PreparedOrder preparedOrder) {
        Order order = preparedOrder.order();
        order.setDiscountAmount(preparedOrder.discountAmount());
        order.setTaxAmount(preparedOrder.taxAmount());
        order.setShippingCost(preparedOrder.shippingCost());
        order.setClimateContributionAmount(preparedOrder.climateContributionAmount());
        order.setCarbonCompensationSelected(preparedOrder.carbonCompensationSelected());
        order.setTotalCo2EmissionKg(preparedOrder.totalCo2EmissionKg());
        order.setTotalPrice(preparedOrder.totalPrice());
        order.setStatus(OrderStatus.CONFIRMED);

        List<Product> updatedProducts = new ArrayList<>();
        for (PreparedOrderItem preparedItem : preparedOrder.items()) {
            Product product = preparedItem.product();
            if (preparedItem.quantity() > product.getStock()) {
                throw new IllegalArgumentException("Only " + product.getStock() + " units of " + product.getName() + " are available");
            }
            product.setStock(product.getStock() - preparedItem.quantity());
            updatedProducts.add(product);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(preparedItem.quantity());
            orderItem.setPriceAtOrderTime(preparedItem.unitPrice());
            order.getItems().add(orderItem);
        }

        Order savedOrder = orderRepository.save(order);
        productRepository.saveAll(updatedProducts);
        return savedOrder;
    }

    /**
     * Looks up and validates a coupon code. Returns null if no code was provided.
     * Throws IllegalArgumentException if the code is invalid, expired, already used,
     * or does not belong to the placing customer.
     */
    private Coupon resolveCoupon(String couponCode, User customer) {
        if (couponCode == null || couponCode.isBlank()) {
            return null;
        }

        Coupon coupon = couponRepository.findByCode(couponCode)
                .orElseThrow(() -> new IllegalArgumentException("Coupon code not found: " + couponCode));

        if (!coupon.getCustomer().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("Coupon does not belong to this customer");
        }
        if (coupon.isUsed()) {
            throw new IllegalArgumentException("Coupon has already been used: " + couponCode);
        }
        if (coupon.getValidUntil() != null && coupon.getValidUntil().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Coupon has expired: " + couponCode);
        }
        return coupon;
    }

    private DeliveryAddressSnapshot resolveDeliveryAddress(User customer, DeliveryAddressRequest deliveryAddressRequest) {
        if (deliveryAddressRequest != null) {
            return new DeliveryAddressSnapshot(
                    deliveryAddressRequest.street(),
                    deliveryAddressRequest.city(),
                    deliveryAddressRequest.postalCode(),
                    deliveryAddressRequest.country()
            );
        }

        DeliveryAddress deliveryAddress = deliveryAddressRepository.findFirstByUserId(customer.getId())
                .orElseThrow(() -> new IllegalArgumentException("Delivery address is required"));
        return new DeliveryAddressSnapshot(
                deliveryAddress.getStreet(),
                deliveryAddress.getCity(),
                deliveryAddress.getPostalCode(),
                deliveryAddress.getCountry()
        );
    }

    private PaymentMethodSnapshot resolvePaymentMethod(User customer, PaymentMethodRequest paymentMethodRequest) {
        if (paymentMethodRequest != null) {
            return new PaymentMethodSnapshot(
                    paymentMethodRequest.methodType(),
                    PaymentMethodSupport.toStoredDetails(paymentMethodRequest),
                    PaymentMethodSupport.buildPreviewLabel(paymentMethodRequest)
            );
        }

        PaymentMethod paymentMethod = paymentMethodRepository.findFirstByUserId(customer.getId())
                .orElseThrow(() -> new IllegalArgumentException("Payment method is required"));
        return new PaymentMethodSnapshot(
                paymentMethod.getMethodType(),
                paymentMethod.getMaskedDetails(),
                paymentMethod.getMethodType() == null
                        ? paymentMethod.getMaskedDetails()
                        : paymentMethod.getMethodType().name() + " " + paymentMethod.getMaskedDetails()
        );
    }

    private PaymentMethodSnapshot resolveGuestPaymentMethod(PaymentMethodRequest paymentMethodRequest) {
        return new PaymentMethodSnapshot(
                paymentMethodRequest.methodType(),
                PaymentMethodSupport.toStoredDetails(paymentMethodRequest),
                PaymentMethodSupport.buildPreviewLabel(paymentMethodRequest)
        );
    }

    private BigDecimal calculateCouponDiscount(BigDecimal subtotal, Coupon coupon) {
        if (coupon == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal multiplier = coupon.getDiscountPercent().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return subtotal.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateClimateContributionAmount(BigDecimal totalCo2EmissionKg, boolean selected) {
        if (!selected || totalCo2EmissionKg == null || totalCo2EmissionKg.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return totalCo2EmissionKg
                .multiply(CARBON_COMPENSATION_RATE_PER_KG)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void markCouponAsUsed(Coupon coupon, Order savedOrder, User customer) {
        if (coupon == null) {
            return;
        }

        coupon.setUsed(true);
        coupon.setUsedAt(Instant.now());
        couponRepository.save(coupon);
        auditLogService.record(
                customer,
                "APPLY_COUPON",
                "Coupon",
                coupon.getId(),
                AuditInitiator.USER,
                "code=" + coupon.getCode() + ", orderId=" + savedOrder.getId());
    }

    private String resolveOrderNumber(String requestedOrderNumber) {
        if (requestedOrderNumber != null && !requestedOrderNumber.isBlank()) {
            return requestedOrderNumber.trim();
        }
        return buildOrderNumber();
    }

    private String buildOrderNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long suffix = Math.abs(System.nanoTime() % 1_000_000);
        return "ORD-" + timestamp + "-" + String.format("%06d", suffix);
    }

    private BigDecimal applyDiscount(BigDecimal price, BigDecimal discountPercent) {
        if (discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) == 0) {
            return price;
        }

        BigDecimal multiplier = BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal.valueOf(100)));
        return price.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(orderItem -> new OrderItemResponse(
                        orderItem.getId(),
                        orderItem.getProduct().getId(),
                        orderItem.getProduct().getName(),
                        orderItem.getProduct().isPurchasable(),
                        orderItem.getQuantity(),
                        orderItem.getPriceAtOrderTime(),
                        orderItem.getPriceAtOrderTime()
                                .multiply(BigDecimal.valueOf(orderItem.getQuantity()))))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerEmail(),
                order.getStatus(),
                order.getTotalPrice(),
                order.getTaxAmount(),
                order.getShippingCost(),
                order.getShippingMethod(),
                order.getClimateContributionAmount(),
                order.getTotalCo2EmissionKg(),
                order.getDiscountAmount(),
                order.getCouponCode(),
                order.getCreatedAt(),
                order.getDeliveredAt(),
                itemResponses,
                order.getTruckIdentifier(),
                estimateDeliveryAt(order),
                null);
    }

    private OrderResponse toResponse(Order order, boolean confirmationEmailSent) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(orderItem -> new OrderItemResponse(
                        orderItem.getId(),
                        orderItem.getProduct().getId(),
                        orderItem.getProduct().getName(),
                        orderItem.getProduct().isPurchasable(),
                        orderItem.getQuantity(),
                        orderItem.getPriceAtOrderTime(),
                        orderItem.getPriceAtOrderTime()
                                .multiply(BigDecimal.valueOf(orderItem.getQuantity()))))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerEmail(),
                order.getStatus(),
                order.getTotalPrice(),
                order.getTaxAmount(),
                order.getShippingCost(),
                order.getShippingMethod(),
                order.getClimateContributionAmount(),
                order.getTotalCo2EmissionKg(),
                order.getDiscountAmount(),
                order.getCouponCode(),
                order.getCreatedAt(),
                order.getDeliveredAt(),
                itemResponses,
                order.getTruckIdentifier(),
                estimateDeliveryAt(order),
                confirmationEmailSent);
    }

    private Instant estimateDeliveryAt(Order order) {
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            return null;
        }

        Duration totalDuration = order.getShippingMethod() == ShippingMethod.EXPRESS
                ? EXPRESS_TOTAL_DELIVERY
                : STANDARD_TOTAL_DELIVERY;
        Instant createdEstimate = order.getCreatedAt().plus(totalDuration);
        Instant minimumFromNow = Instant.now().plus(resolveMinimumRemaining(order));

        return createdEstimate.isAfter(minimumFromNow) ? createdEstimate : minimumFromNow;
    }

    private Duration resolveMinimumRemaining(Order order) {
        boolean expressShipping = order.getShippingMethod() == ShippingMethod.EXPRESS;

        return switch (order.getStatus()) {
            case PENDING, CONFIRMED -> expressShipping
                    ? EXPRESS_MIN_REMAINING_EARLY
                    : STANDARD_MIN_REMAINING_EARLY;
            case PACKED_IN_WAREHOUSE -> expressShipping
                    ? EXPRESS_MIN_REMAINING_PACKED
                    : STANDARD_MIN_REMAINING_PACKED;
            case IN_TRUCK -> expressShipping
                    ? EXPRESS_MIN_REMAINING_TRUCK
                    : STANDARD_MIN_REMAINING_TRUCK;
            case SHIPPED -> expressShipping
                    ? EXPRESS_MIN_REMAINING_SHIPPED
                    : STANDARD_MIN_REMAINING_SHIPPED;
            case DELIVERED, CANCELLED -> Duration.ZERO;
        };
    }

    private OrderPreviewResponse toPreviewResponse(PreparedOrder preparedOrder) {
        List<OrderPreviewItemResponse> itemResponses = preparedOrder.items().stream()
                .map(item -> new OrderPreviewItemResponse(
                        item.product().getId(),
                        item.product().getName(),
                        item.quantity(),
                        item.unitPrice(),
                        item.lineTotal()
                ))
                .toList();

        return new OrderPreviewResponse(
                preparedOrder.order().getOrderNumber(),
                preparedOrder.order().getCustomerEmail(),
                preparedOrder.subtotal(),
                preparedOrder.discountAmount(),
                preparedOrder.taxAmount(),
                preparedOrder.shippingCost(),
                preparedOrder.climateContributionAmount(),
                preparedOrder.totalCo2EmissionKg(),
                preparedOrder.totalPrice(),
                preparedOrder.order().getCouponCode(),
                itemResponses
        );
    }

    private boolean sendOrderConfirmation(Order order) {
        StringBuilder body = new StringBuilder()
                .append("Vielen Dank fuer deine Bestellung.\n\n")
                .append("Bestellnummer: ").append(order.getOrderNumber()).append('\n')
                .append("Gesamtbetrag: ").append(order.getTotalPrice()).append(" EUR\n")
                .append("Versand an: ").append(order.getDeliveryStreet()).append(", ")
                .append(order.getDeliveryPostalCode()).append(' ')
                .append(order.getDeliveryCity()).append(", ")
                .append(order.getDeliveryCountry()).append('\n');

        if (order.getClimateContributionAmount() != null
                && order.getClimateContributionAmount().compareTo(BigDecimal.ZERO) > 0) {
            body.append("Klimabeitrag: ")
                    .append(order.getClimateContributionAmount())
                    .append(" EUR\n");
        }

        body.append("\nArtikel:\n");

        for (OrderItem item : order.getItems()) {
            body.append("- ")
                    .append(item.getProduct().getName())
                    .append(" x")
                    .append(item.getQuantity())
                    .append(" = ")
                    .append(item.getPriceAtOrderTime().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .append(" EUR\n");
        }

        return emailService.sendEmail(
                order.getCustomerEmail(),
                "Bestellbestaetigung " + order.getOrderNumber(),
                body.toString()
        );
    }

    private record DeliveryAddressSnapshot(
            String street,
            String city,
            String postalCode,
            String country
    ) {}

    private record PaymentMethodSnapshot(
            de.fhdw.webshop.user.PaymentMethodType methodType,
            String maskedDetails,
            String previewLabel
    ) {}

    private record RequestedOrderItem(
            Product product,
            int quantity
    ) {}

    private record PreparedOrderItem(
            Product product,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}

    private record PreparedOrder(
            Order order,
            List<PreparedOrderItem> items,
            Coupon coupon,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal subtotal,
            BigDecimal shippingCost,
            BigDecimal climateContributionAmount,
            BigDecimal totalCo2EmissionKg,
            boolean carbonCompensationSelected,
            BigDecimal totalPrice
    ) {}
}
