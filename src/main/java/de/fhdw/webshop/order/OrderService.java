package de.fhdw.webshop.order;

import de.fhdw.webshop.cart.CartItem;
import de.fhdw.webshop.cart.CartRepository;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.19");

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductService.DiscountLookupPort discountLookupPort;

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

    /** US #42 — Convert the current cart into a confirmed order. */
    @Transactional
    public OrderResponse placeOrder(User customer, PlaceOrderRequest placeOrderRequest) {
        List<CartItem> cartItems = cartRepository.findByUserId(customer.getId());
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cannot place an order with an empty cart");
        }

        Order order = new Order();
        order.setCustomer(customer);
        order.setCouponCode(placeOrderRequest != null ? placeOrderRequest.couponCode() : null);

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

        BigDecimal taxAmount = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        order.setTotalPrice(subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP));
        order.setTaxAmount(taxAmount);
        order.setShippingCost(BigDecimal.ZERO);
        order.setStatus(OrderStatus.CONFIRMED);

        Order savedOrder = orderRepository.save(order);
        cartRepository.deleteByUserId(customer.getId());
        return toResponse(savedOrder);
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
