package de.fhdw.webshop.cart;

import de.fhdw.webshop.cart.dto.AddToCartRequest;
import de.fhdw.webshop.cart.dto.CartItemResponse;
import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductService productService;
    private final ProductService.DiscountLookupPort discountLookupPort;
    private final OrderRepository orderRepository;

    private static final BigDecimal TAX_RATE      = BigDecimal.valueOf(0.19);
    private static final BigDecimal SHIPPING_COST = new BigDecimal("4.99");

    /** US #41/#43 — Returns the cart with a full price breakdown (subtotal, 19% VAT, shipping, total). */
    public CartResponse getCart(Long userId) {
        List<CartItem> cartItems = cartRepository.findByUserId(userId);
        List<CartItemResponse> itemResponses = cartItems.stream()
                .map(cartItem -> toItemResponse(cartItem, userId))
                .toList();

        BigDecimal subtotal = itemResponses.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        BigDecimal tax = subtotal.multiply(TAX_RATE)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        BigDecimal shippingCost = itemResponses.isEmpty() ? BigDecimal.ZERO : SHIPPING_COST;

        BigDecimal total = subtotal.add(tax).add(shippingCost)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        return new CartResponse(itemResponses, subtotal, tax, shippingCost, total);
    }

    /** US #39 — Add an item to the cart; if already present, increase quantity. */
    @Transactional
    public CartResponse addItem(User user, AddToCartRequest addToCartRequest) {
        Product product = productService.loadProduct(addToCartRequest.productId());
        if (!product.isPurchasable()) {
            throw new IllegalArgumentException("Product is not available for purchase: " + product.getId());
        }

        CartItem cartItem = cartRepository
                .findByUserIdAndProductId(user.getId(), addToCartRequest.productId())
                .orElseGet(() -> {
                    CartItem newCartItem = new CartItem();
                    newCartItem.setUser(user);
                    newCartItem.setProduct(product);
                    newCartItem.setQuantity(0);
                    return newCartItem;
                });

        cartItem.setQuantity(cartItem.getQuantity() + addToCartRequest.quantity());
        cartRepository.save(cartItem);
        return getCart(user.getId());
    }

    /** US #40 — Remove a specific product from the cart. */
    @Transactional
    public CartResponse removeItem(User user, Long productId) {
        cartRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new EntityNotFoundException("Item not in cart: productId=" + productId));
        cartRepository.deleteByUserIdAndProductId(user.getId(), productId);
        return getCart(user.getId());
    }

    /**
     * US #50 — Re-add all still-purchasable items from a past order into the cart.
     * Items whose products are no longer purchasable are silently skipped (US #49).
     */
    @Transactional
    public CartResponse reorder(User user, Long orderId) {
        Order previousOrder = orderRepository.findByIdAndCustomerId(orderId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        for (OrderItem orderItem : previousOrder.getItems()) {
            Product product = orderItem.getProduct();
            if (!product.isPurchasable()) {
                continue;
            }
            CartItem cartItem = cartRepository
                    .findByUserIdAndProductId(user.getId(), product.getId())
                    .orElseGet(() -> {
                        CartItem newCartItem = new CartItem();
                        newCartItem.setUser(user);
                        newCartItem.setProduct(product);
                        newCartItem.setQuantity(0);
                        return newCartItem;
                    });
            cartItem.setQuantity(cartItem.getQuantity() + orderItem.getQuantity());
            cartRepository.save(cartItem);
        }
        return getCart(user.getId());
    }

    /** Called by OrderService after checkout to clear the cart. */
    @Transactional
    public void clearCart(Long userId) {
        cartRepository.deleteByUserId(userId);
    }

    private CartItemResponse toItemResponse(CartItem cartItem, Long userId) {
        BigDecimal unitPrice = discountLookupPort.findBestActiveDiscountPercent(userId, cartItem.getProduct().getId());
        // Calculate effective price applying discount
        BigDecimal recommendedRetailPrice = cartItem.getProduct().getRecommendedRetailPrice();
        BigDecimal effectiveUnitPrice;
        if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal multiplier = BigDecimal.ONE.subtract(unitPrice.divide(BigDecimal.valueOf(100)));
            effectiveUnitPrice = recommendedRetailPrice.multiply(multiplier)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        } else {
            effectiveUnitPrice = recommendedRetailPrice;
        }
        BigDecimal lineTotal = effectiveUnitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));

        return new CartItemResponse(
                cartItem.getId(),
                cartItem.getProduct().getId(),
                cartItem.getProduct().getName(),
                cartItem.getProduct().getImageUrl(),
                effectiveUnitPrice,
                cartItem.getQuantity(),
                lineTotal,
                cartItem.getAddedAt()
        );
    }
}
