package de.fhdw.webshop.order;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.cart.CartItem;
import de.fhdw.webshop.cart.CartRepository;
import de.fhdw.webshop.discount.Coupon;
import de.fhdw.webshop.discount.CouponRepository;
import de.fhdw.webshop.order.dto.OrderItemResponse;
import de.fhdw.webshop.order.dto.OrderResponse;
import de.fhdw.webshop.order.dto.PlaceOrderRequest;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.19");

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CouponRepository couponRepository;
    private final ProductService.DiscountLookupPort discountLookupPort;
    private final AuditLogService auditLogService;

    public List<OrderResponse> listOrdersForCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public OrderResponse getOrder(Long orderId, Long customerId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        return toResponse(order);
    }

    /** US #42 — Convert the current cart into a confirmed order. Coupon reduces the order subtotal. */
    @Transactional
    public OrderResponse placeOrder(User customer, PlaceOrderRequest placeOrderRequest) {
        List<CartItem> cartItems = cartRepository.findByUserId(customer.getId());
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cannot place an order with an empty cart");
        }

        String couponCode = placeOrderRequest != null ? placeOrderRequest.couponCode() : null;
        Coupon coupon = resolveCoupon(couponCode, customer);

        Order order = new Order();
        order.setCustomer(customer);
        order.setCouponCode(couponCode);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cartItems) {
            BigDecimal discountPercent = discountLookupPort
                    .findBestActiveDiscountPercent(customer.getId(), cartItem.getProduct().getId());
            BigDecimal unitPrice = applyDiscount(cartItem.getProduct().getRecommendedRetailPrice(), discountPercent);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPriceAtOrderTime(unitPrice);
            order.getItems().add(orderItem);

            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        // Apply coupon discount to the subtotal (after item-level discounts)
        if (coupon != null) {
            subtotal = applyDiscount(subtotal, coupon.getDiscountPercent());
        }

        BigDecimal taxAmount = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        order.setTotalPrice(subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP));
        order.setTaxAmount(taxAmount);
        order.setShippingCost(BigDecimal.ZERO);
        order.setStatus(OrderStatus.CONFIRMED);

        Order savedOrder = orderRepository.save(order);
        cartRepository.deleteByUserId(customer.getId());

        if (coupon != null) {
            coupon.setUsed(true);
            coupon.setUsedAt(Instant.now());
            couponRepository.save(coupon);
            auditLogService.record(customer, "APPLY_COUPON", "Coupon", coupon.getId(),
                    AuditInitiator.USER,
                    "code=" + coupon.getCode() + ", orderId=" + savedOrder.getId());
        }

        return toResponse(savedOrder);
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
                                .multiply(BigDecimal.valueOf(orderItem.getQuantity()))
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalPrice(),
                order.getTaxAmount(),
                order.getShippingCost(),
                order.getCouponCode(),
                order.getCreatedAt(),
                itemResponses
        );
    }
}
