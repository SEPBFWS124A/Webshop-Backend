package de.fhdw.webshop.order;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.address.AddressLookupService;
import de.fhdw.webshop.address.AddressValidationRequest;
import de.fhdw.webshop.address.AddressValidationResponse;
import de.fhdw.webshop.accountlink.AccountLinkRepository;
import de.fhdw.webshop.agb.AgbService;
import de.fhdw.webshop.cart.CartItem;
import de.fhdw.webshop.cart.CartService;
import de.fhdw.webshop.cart.CartRepository;
import de.fhdw.webshop.discount.Coupon;
import de.fhdw.webshop.discount.CouponRepository;
import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.order.dto.OrderItemResponse;
import de.fhdw.webshop.order.dto.OrderApprovalResponse;
import de.fhdw.webshop.order.dto.OrderPreviewItemResponse;
import de.fhdw.webshop.order.dto.OrderPreviewResponse;
import de.fhdw.webshop.order.dto.OrderResponse;
import de.fhdw.webshop.order.dto.PlaceOrderRequest;
import de.fhdw.webshop.pickup.PickupStore;
import de.fhdw.webshop.pickup.PickupStoreRepository;
import de.fhdw.webshop.pickup.PickupStoreService;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.user.DeliveryAddress;
import de.fhdw.webshop.user.DeliveryAddressRepository;
import de.fhdw.webshop.user.PaymentMethod;
import de.fhdw.webshop.user.PaymentMethodRepository;
import de.fhdw.webshop.user.PaymentMethodSupport;
import de.fhdw.webshop.user.PaymentMethodType;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserService;
import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserRepository userRepository;
    private final AgbService agbService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final AddressLookupService addressLookupService;
    private final AccountLinkRepository accountLinkRepository;
    private final PickupStoreRepository pickupStoreRepository;
    private final PickupStoreService pickupStoreService;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

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

    @Transactional
    public OrderResponse cancelOrder(Long orderId, User customer) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customer.getId())
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (!canCustomerCancel(order)) {
            throw new IllegalArgumentException("Bestellungen koennen nur im Status Aufgegeben storniert werden.");
        }

        restoreReservedStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);

        auditLogService.record(customer, "CANCEL_ORDER", "Order", savedOrder.getId(),
                AuditInitiator.USER,
                "Status geaendert auf Storniert fuer Bestellung " + savedOrder.getOrderNumber());
        triggerRefundIfRequired(savedOrder, customer);

        return toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderApprovalResponse> listPendingApprovalRequests(User manager) {
        requireBusinessApprovalManager(manager);
        return orderRepository.findApprovalRequestsForManager(manager.getId(), OrderStatus.Pending_Approval).stream()
                .map(order -> toApprovalResponse(order, null))
                .toList();
    }

    @Transactional
    public OrderApprovalResponse approveApprovalRequest(User manager, Long orderId) {
        requireBusinessApprovalManager(manager);
        Order order = loadApprovalRequestForManager(manager, orderId);
        requirePendingApproval(order);

        List<Product> updatedProducts = new ArrayList<>();
        for (OrderItem orderItem : order.getItems()) {
            Product product = orderItem.getProduct();
            if (!product.isPurchasable() || product.getStock() <= 0) {
                throw new IllegalArgumentException(product.getName() + " is no longer available");
            }
            if (orderItem.getQuantity() > product.getStock()) {
                throw new IllegalArgumentException("Only " + product.getStock() + " units of " + product.getName() + " are available");
            }
            product.setStock(product.getStock() - orderItem.getQuantity());
            updatedProducts.add(product);
        }

        order.setStatus(OrderStatus.CONFIRMED);
        order.setApprovalDecidedAt(Instant.now());
        order.setApprovalDecidedBy(manager);
        order.setApprovalRejectionReason(null);
        Order savedOrder = orderRepository.save(order);
        productRepository.saveAll(updatedProducts);
        markCouponAsUsedByCode(savedOrder, manager);
        auditLogService.record(manager, "APPROVE_ORDER_REQUEST", "Order", savedOrder.getId(),
                AuditInitiator.USER,
                "Manager approved order request " + savedOrder.getOrderNumber() + " for customer "
                        + savedOrder.getCustomer().getId());
        boolean confirmationEmailSent = sendOrderConfirmation(savedOrder);
        return toApprovalResponse(savedOrder, confirmationEmailSent);
    }

    @Transactional
    public OrderApprovalResponse rejectApprovalRequest(User manager, Long orderId, String reason) {
        requireBusinessApprovalManager(manager);
        Order order = loadApprovalRequestForManager(manager, orderId);
        requirePendingApproval(order);
        String normalizedReason = normalizeRequiredRejectionReason(reason);

        order.setStatus(OrderStatus.Rejected);
        order.setApprovalRejectionReason(normalizedReason);
        order.setApprovalDecidedAt(Instant.now());
        order.setApprovalDecidedBy(manager);
        Order savedOrder = orderRepository.save(order);
        auditLogService.record(manager, "REJECT_ORDER_REQUEST", "Order", savedOrder.getId(),
                AuditInitiator.USER,
                "Manager rejected order request " + savedOrder.getOrderNumber() + " for customer "
                        + savedOrder.getCustomer().getId());
        sendApprovalRejectedNotification(savedOrder, normalizedReason);
        return toApprovalResponse(savedOrder, null);
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
        boolean needsAcceptance = agbService.userNeedsToAcceptAgb(customer);
        if (needsAcceptance) {
            if (!Boolean.TRUE.equals(placeOrderRequest != null ? placeOrderRequest.acceptedTermsAndConditions() : null)) {
                throw new IllegalArgumentException("Bitte akzeptiere die aktuellen AGB vor der Bestellung.");
            }
            customer.setAgbAcceptedAt(Instant.now());
            userRepository.save(customer);
        }
        if (placeOrderRequest == null || !Boolean.TRUE.equals(placeOrderRequest.acceptedPrivacyPolicy())) {
            throw new IllegalArgumentException("Bitte akzeptiere AGB, Widerrufsbelehrung und Datenschutzhinweise vor der Bestellung");
        }
        PreparedOrder preparedOrder = prepareCustomerOrder(customer, placeOrderRequest);
        DeliveryAddressRequest deliveryAddressRequest = placeOrderRequest != null ? placeOrderRequest.deliveryAddress() : null;
        PaymentMethodRequest paymentMethodRequest = placeOrderRequest != null ? placeOrderRequest.paymentMethod() : null;
        if (placeOrderRequest != null && Boolean.TRUE.equals(placeOrderRequest.saveDeliveryAddress()) && deliveryAddressRequest != null) {
            userService.saveDeliveryAddress(customer, deliveryAddressRequest);
        }
        if (placeOrderRequest != null && Boolean.TRUE.equals(placeOrderRequest.savePaymentMethod()) && paymentMethodRequest != null) {
            userService.savePaymentMethod(customer, paymentMethodRequest);
        }
        if (preparedOrder.approvalRequired()) {
            Order savedOrder = persistApprovalRequest(preparedOrder, placeOrderRequest != null ? placeOrderRequest.approvalReason() : null);
            cartService.clearCartSilently(customer.getId());
            return toResponse(savedOrder);
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
        PickupStore pickupStore = resolvePickupStore(placeOrderRequest);
        String orderNumber = placeOrderRequest != null ? placeOrderRequest.previewOrderNumber() : null;
        boolean carbonCompensationSelected = placeOrderRequest != null && Boolean.TRUE.equals(placeOrderRequest.carbonCompensationSelected());
        BigDecimal approvalBudgetLimit = resolveApprovalBudgetLimit(customer);
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
                        .map(cartItem -> new RequestedOrderItem(
                                cartItem.getProduct(),
                                cartItem.getQuantity(),
                                cartItem.getPersonalizationText()))
                        .toList(),
                deliveryAddress,
                shippingMethod,
                paymentMethod,
                coupon,
                customer.getId(),
                placeOrderRequest != null && Boolean.TRUE.equals(placeOrderRequest.allowUnverifiedAddress()),
                carbonCompensationSelected,
                approvalBudgetLimit,
                pickupStore
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
                .map(item -> new RequestedOrderItem(
                        productService.loadProduct(item.productId()),
                        item.quantity(),
                        item.personalizationText()))
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
                Boolean.TRUE.equals(placeOrderRequest.carbonCompensationSelected()),
                null,
                resolvePickupStore(placeOrderRequest)
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
                                       boolean carbonCompensationSelected,
                                       BigDecimal approvalBudgetLimit,
                                       PickupStore pickupStore) {
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
        order.setPickupStore(pickupStore);

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
            String personalizationText = normalizePersonalizationText(product, requestedItem.personalizationText());

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
            preparedItems.add(new PreparedOrderItem(product, requestedItem.quantity(), personalizationText, unitPrice, lineTotal));
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
        boolean approvalRequired = approvalBudgetLimit != null && totalPrice.compareTo(approvalBudgetLimit) > 0;
        if (approvalRequired) {
            order.setApprovalBudgetLimit(approvalBudgetLimit);
        }

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
                totalPrice,
                approvalRequired,
                approvalRequired ? approvalBudgetLimit : null
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

    private PickupStore resolvePickupStore(PlaceOrderRequest placeOrderRequest) {
        if (placeOrderRequest == null || placeOrderRequest.pickupStoreId() == null) {
            return null;
        }

        return pickupStoreRepository.findByIdAndActiveTrue(placeOrderRequest.pickupStoreId())
                .orElseThrow(() -> new IllegalArgumentException("Der ausgewaehlte Abhol-Store ist nicht verfuegbar"));
    }

    private void validateLegalAcceptance(PlaceOrderRequest placeOrderRequest) {
        if (placeOrderRequest == null
                || !Boolean.TRUE.equals(placeOrderRequest.acceptedTermsAndConditions())
                || !Boolean.TRUE.equals(placeOrderRequest.acceptedPrivacyPolicy())) {
            throw new IllegalArgumentException("Bitte akzeptiere AGB, Widerrufsbelehrung und Datenschutzhinweise vor der Bestellung");
        }
    }

    private BigDecimal resolveApprovalBudgetLimit(User customer) {
        if (customer == null) {
            return null;
        }

        return accountLinkRepository.findAllForUserId(customer.getId()).stream()
                .map(link -> link.getMaxOrderValueLimit())
                .filter(limit -> limit != null && limit.compareTo(BigDecimal.ZERO) > 0)
                .min(BigDecimal::compareTo)
                .orElse(null);
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
            orderItem.setPersonalizationText(preparedItem.personalizationText());
            orderItem.setPriceAtOrderTime(preparedItem.unitPrice());
            order.getItems().add(orderItem);
        }

        Order savedOrder = orderRepository.save(order);
        productRepository.saveAll(updatedProducts);
        return savedOrder;
    }

    private Order persistApprovalRequest(PreparedOrder preparedOrder, String approvalReason) {
        Order order = preparedOrder.order();
        order.setDiscountAmount(preparedOrder.discountAmount());
        order.setTaxAmount(preparedOrder.taxAmount());
        order.setShippingCost(preparedOrder.shippingCost());
        order.setClimateContributionAmount(preparedOrder.climateContributionAmount());
        order.setCarbonCompensationSelected(preparedOrder.carbonCompensationSelected());
        order.setTotalCo2EmissionKg(preparedOrder.totalCo2EmissionKg());
        order.setTotalPrice(preparedOrder.totalPrice());
        order.setStatus(OrderStatus.Pending_Approval);
        order.setApprovalBudgetLimit(preparedOrder.approvalBudgetLimit());
        order.setApprovalReason(normalizeApprovalReason(approvalReason));

        for (PreparedOrderItem preparedItem : preparedOrder.items()) {
            Product product = preparedItem.product();
            if (preparedItem.quantity() > product.getStock()) {
                throw new IllegalArgumentException("Only " + product.getStock() + " units of " + product.getName() + " are available");
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(preparedItem.quantity());
            orderItem.setPersonalizationText(preparedItem.personalizationText());
            orderItem.setPriceAtOrderTime(preparedItem.unitPrice());
            order.getItems().add(orderItem);
        }

        Order savedOrder = orderRepository.save(order);
        sendApprovalRequestNotifications(savedOrder);
        return savedOrder;
    }

    private String normalizeApprovalReason(String approvalReason) {
        if (approvalReason == null || approvalReason.isBlank()) {
            return null;
        }

        String normalizedReason = approvalReason.trim();
        if (normalizedReason.length() > 1000) {
            throw new IllegalArgumentException("Die Begruendung darf maximal 1000 Zeichen lang sein");
        }
        return normalizedReason;
    }

    private String normalizeRequiredRejectionReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Ablehnungsgrund eingeben");
        }

        String normalizedReason = reason.trim();
        if (normalizedReason.length() > 1000) {
            throw new IllegalArgumentException("Die Begruendung darf maximal 1000 Zeichen lang sein");
        }
        return normalizedReason;
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

    private void markCouponAsUsedByCode(Order savedOrder, User manager) {
        String couponCode = savedOrder.getCouponCode();
        if (couponCode == null || couponCode.isBlank()) {
            return;
        }

        Coupon coupon = couponRepository.findByCode(couponCode)
                .orElseThrow(() -> new IllegalArgumentException("Coupon code not found: " + couponCode));
        if (savedOrder.getCustomer() == null || !coupon.getCustomer().getId().equals(savedOrder.getCustomer().getId())) {
            throw new IllegalArgumentException("Coupon does not belong to the approved customer");
        }
        if (coupon.isUsed()) {
            throw new IllegalArgumentException("Coupon has already been used: " + couponCode);
        }

        coupon.setUsed(true);
        coupon.setUsedAt(Instant.now());
        couponRepository.save(coupon);
        auditLogService.record(
                savedOrder.getCustomer(),
                "APPLY_COUPON",
                "Coupon",
                coupon.getId(),
                AuditInitiator.USER,
                "code=" + coupon.getCode() + ", orderId=" + savedOrder.getId()
                        + ", approvedBy=" + manager.getId());
    }

    private boolean canCustomerCancel(Order order) {
        return order.getStatus() == OrderStatus.CONFIRMED;
    }

    private void restoreReservedStock(Order order) {
        List<Product> updatedProducts = new ArrayList<>();
        for (OrderItem orderItem : order.getItems()) {
            Product product = orderItem.getProduct();
            product.setStock(product.getStock() + orderItem.getQuantity());
            updatedProducts.add(product);
        }

        if (!updatedProducts.isEmpty()) {
            productRepository.saveAll(updatedProducts);
        }
    }

    private void triggerRefundIfRequired(Order order, User customer) {
        if (order.getPaymentMethodType() != PaymentMethodType.CREDIT_CARD
                && order.getPaymentMethodType() != PaymentMethodType.SEPA_DIRECT_DEBIT) {
            return;
        }

        auditLogService.record(customer, "ORDER_REFUND_TRIGGERED", "Order", order.getId(),
                AuditInitiator.SYSTEM,
                "Automatische Rueckerstattung fuer " + order.getPaymentMethodType()
                        + " angestossen, Betrag=" + order.getTotalPrice());
    }

    private void requireBusinessApprovalManager(User manager) {
        if (manager == null
                || manager.getUserType() != UserType.BUSINESS
                || !manager.hasRole(UserRole.CUSTOMER)) {
            throw new IllegalArgumentException("Order approvals are only available for B2B customer administrators");
        }
    }

    private Order loadApprovalRequestForManager(User manager, Long orderId) {
        return orderRepository.findApprovalRequestForManager(orderId, manager.getId())
                .orElseThrow(() -> new EntityNotFoundException("Order approval request not found: " + orderId));
    }

    private void requirePendingApproval(Order order) {
        if (order.getStatus() != OrderStatus.Pending_Approval) {
            throw new IllegalArgumentException("Only pending approval requests can be processed");
        }
    }

    private void sendApprovalRequestNotifications(Order order) {
        if (order.getCustomer() == null) {
            return;
        }

        List<User> managers = accountLinkRepository.findAllForUserId(order.getCustomer().getId()).stream()
                .map(link -> link.getUserA().getId().equals(order.getCustomer().getId()) ? link.getUserB() : link.getUserA())
                .filter(manager -> manager != null
                        && !manager.getId().equals(order.getCustomer().getId())
                        && manager.isActive()
                        && manager.getUserType() == UserType.BUSINESS
                        && manager.hasRole(UserRole.CUSTOMER))
                .distinct()
                .toList();

        for (User manager : managers) {
            emailService.sendEmail(
                    manager.getEmail(),
                    "Freigabe erforderlich: " + order.getOrderNumber(),
                    buildApprovalRequestEmailBody(order, manager)
            );
        }
    }

    private void sendApprovalRejectedNotification(Order order, String reason) {
        emailService.sendEmail(
                order.getCustomerEmail(),
                "Freigabe abgelehnt: " + order.getOrderNumber(),
                buildApprovalRejectedEmailBody(order, reason)
        );
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
                        toVariantValues(orderItem.getProduct()),
                        orderItem.getPersonalizationText(),
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
                order.getApprovalReason(),
                order.getApprovalBudgetLimit(),
                pickupStoreService.toResponse(order.getPickupStore()),
                null);
    }

    private OrderApprovalResponse toApprovalResponse(Order order, Boolean confirmationEmailSent) {
        User requester = order.getCustomer();
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(orderItem -> new OrderItemResponse(
                        orderItem.getId(),
                        orderItem.getProduct().getId(),
                        orderItem.getProduct().getName(),
                        toVariantValues(orderItem.getProduct()),
                        orderItem.getPersonalizationText(),
                        orderItem.getProduct().isPurchasable(),
                        orderItem.getQuantity(),
                        orderItem.getPriceAtOrderTime(),
                        orderItem.getPriceAtOrderTime()
                                .multiply(BigDecimal.valueOf(orderItem.getQuantity()))))
                .toList();

        return new OrderApprovalResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                requester != null ? requester.getId() : null,
                requester != null ? requester.getUsername() : order.getCustomerName(),
                requester != null ? requester.getEmail() : order.getCustomerEmail(),
                order.getCreatedAt(),
                order.getTotalPrice(),
                order.getTaxAmount(),
                order.getShippingCost(),
                order.getShippingMethod(),
                order.getDiscountAmount(),
                order.getCouponCode(),
                order.getApprovalReason(),
                order.getApprovalBudgetLimit(),
                order.getApprovalRejectionReason(),
                order.getApprovalDecidedAt(),
                itemResponses,
                confirmationEmailSent);
    }

    private OrderResponse toResponse(Order order, boolean confirmationEmailSent) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(orderItem -> new OrderItemResponse(
                        orderItem.getId(),
                        orderItem.getProduct().getId(),
                        orderItem.getProduct().getName(),
                        toVariantValues(orderItem.getProduct()),
                        orderItem.getPersonalizationText(),
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
                order.getApprovalReason(),
                order.getApprovalBudgetLimit(),
                pickupStoreService.toResponse(order.getPickupStore()),
                confirmationEmailSent);
    }

    private Instant estimateDeliveryAt(Order order) {
        if (order.getStatus() == OrderStatus.Pending_Approval
                || order.getStatus() == OrderStatus.Rejected
                || order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.CANCELLED) {
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
            case Pending_Approval, Rejected -> Duration.ZERO;
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
                        toVariantValues(item.product()),
                        item.personalizationText(),
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
                preparedOrder.approvalRequired(),
                preparedOrder.approvalBudgetLimit(),
                itemResponses
        );
    }

    private boolean sendOrderConfirmation(Order order) {
        StringBuilder body = new StringBuilder()
                .append("Vielen Dank fuer deine Bestellung.\n\n")
                .append("Bestellnummer: ").append(order.getOrderNumber()).append('\n')
                .append("Gesamtbetrag: ").append(order.getTotalPrice()).append(" EUR\n");

        if (order.getPickupStore() != null) {
            PickupStore pickupStore = order.getPickupStore();
            body.append("Abholung im Store: ")
                    .append(pickupStore.getName()).append(", ")
                    .append(pickupStore.getStreet()).append(", ")
                    .append(pickupStore.getPostalCode()).append(' ')
                    .append(pickupStore.getCity()).append('\n')
                    .append("Oeffnungszeiten: ")
                    .append(pickupStore.getOpeningHours()).append('\n');
        } else {
            body.append("Versand an: ").append(order.getDeliveryStreet()).append(", ")
                    .append(order.getDeliveryPostalCode()).append(' ')
                    .append(order.getDeliveryCity()).append(", ")
                    .append(order.getDeliveryCountry()).append('\n');
        }

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
            if (item.getPersonalizationText() != null && !item.getPersonalizationText().isBlank()) {
                body.append("  Wunschtext: ")
                        .append(item.getPersonalizationText())
                        .append('\n');
            }
        }

        return emailService.sendEmail(
                order.getCustomerEmail(),
                "Bestellbestaetigung " + order.getOrderNumber(),
                body.toString()
        );
    }

    private Map<String, String> toVariantValues(Product product) {
        Map<String, String> values = new LinkedHashMap<>();
        product.getVariantOptions().stream()
                .sorted((left, right) -> Integer.compare(left.getDisplayOrder(), right.getDisplayOrder()))
                .forEach(option -> values.put(option.getAttributeName(), option.getAttributeValue()));
        return values;
    }

    private String normalizePersonalizationText(Product product, String personalizationText) {
        String normalizedText = trimToNull(personalizationText);
        if (!product.isPersonalizable()) {
            if (normalizedText != null) {
                throw new IllegalArgumentException("Dieser Artikel ist nicht personalisierbar.");
            }
            return null;
        }
        if (normalizedText == null) {
            return null;
        }
        Integer maxLength = product.getPersonalizationMaxLength();
        if (maxLength != null && normalizedText.length() > maxLength) {
            throw new IllegalArgumentException("Der Wunschtext darf maximal " + maxLength + " Zeichen lang sein.");
        }
        return normalizedText;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String buildApprovalRequestEmailBody(Order order, User manager) {
        String requesterName = order.getCustomerName() != null && !order.getCustomerName().isBlank()
                ? order.getCustomerName()
                : order.getCustomer().getUsername();

        StringBuilder body = new StringBuilder()
                .append("Hallo ").append(manager.getUsername()).append(",\n\n")
                .append("eine Bestellung von ").append(requesterName)
                .append(" wartet auf deine Freigabe.\n\n")
                .append("Bestellnummer: ").append(order.getOrderNumber()).append('\n')
                .append("Gesamtbetrag: ").append(order.getTotalPrice()).append(" EUR\n");

        if (order.getApprovalBudgetLimit() != null) {
            body.append("Budgetlimit: ").append(order.getApprovalBudgetLimit()).append(" EUR\n");
        }
        if (order.getApprovalReason() != null && !order.getApprovalReason().isBlank()) {
            body.append("Begruendung: ").append(order.getApprovalReason()).append('\n');
        }

        body.append("\nLink zur Anfrage: ").append(buildApprovalRequestLink()).append('\n')
                .append("\nBitte pruefe die Anfrage zeitnah im Profilbereich.");

        return body.toString();
    }

    private String buildApprovalRejectedEmailBody(Order order, String reason) {
        String requesterName = order.getCustomerName() != null && !order.getCustomerName().isBlank()
                ? order.getCustomerName()
                : "dein Team";

        return new StringBuilder()
                .append("Hallo ").append(requesterName).append(",\n\n")
                .append("deine Bestellanfrage wurde abgelehnt.\n\n")
                .append("Bestellnummer: ").append(order.getOrderNumber()).append('\n')
                .append("Gesamtbetrag: ").append(order.getTotalPrice()).append(" EUR\n")
                .append("Begruendung des Managers: ").append(reason).append('\n')
                .append("\nBitte passe die Bestellung bei Bedarf an und reiche sie erneut ein.")
                .toString();
    }

    private String buildApprovalRequestLink() {
        String normalizedBaseUrl = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "http://localhost:5173"
                : frontendBaseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl + "/profile";
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
            int quantity,
            String personalizationText
    ) {}

    private record PreparedOrderItem(
            Product product,
            int quantity,
            String personalizationText,
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
            BigDecimal totalPrice,
            boolean approvalRequired,
            BigDecimal approvalBudgetLimit
    ) {}
}
