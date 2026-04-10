package de.fhdw.webshop.cart;

import de.fhdw.webshop.cart.dto.AddToCartRequest;
import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** US #41 — View own cart. */
    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(cartService.getCart(currentUser.getId()));
    }

    /** US #39 — Add an item to own cart. */
    @PostMapping("/items")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> addItem(@AuthenticationPrincipal User currentUser,
                                                @Valid @RequestBody AddToCartRequest addToCartRequest) {
        return ResponseEntity.ok(cartService.addItem(currentUser, addToCartRequest));
    }

    /** US #40 — Remove an item from own cart. */
    @DeleteMapping("/items/{productId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> removeItem(@AuthenticationPrincipal User currentUser,
                                                   @PathVariable Long productId) {
        return ResponseEntity.ok(cartService.removeItem(currentUser, productId));
    }

    /** US #50 — Add all re-orderable items from a previous order into the cart. */
    @PostMapping("/reorder/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> reorder(@AuthenticationPrincipal User currentUser,
                                                @PathVariable Long orderId) {
        return ResponseEntity.ok(cartService.reorder(currentUser, orderId));
    }
}
