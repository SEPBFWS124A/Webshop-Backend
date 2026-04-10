package de.fhdw.webshop.order;

import de.fhdw.webshop.order.dto.OrderResponse;
import de.fhdw.webshop.order.dto.PlaceOrderRequest;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** US #48 — Customer views their own order history. */
    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<OrderResponse>> listOrders(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(orderService.listOrdersForCustomer(currentUser.getId()));
    }

    /**
     * US #49 — View a single order; the response includes purchasable flag per item
     * so the frontend can show which items are still available for re-order.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id,
                                                  @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(orderService.getOrder(id, currentUser.getId()));
    }

    /** US #42 — Place an order from the current cart contents. */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> placeOrder(@AuthenticationPrincipal User currentUser,
                                                    @RequestBody(required = false) PlaceOrderRequest placeOrderRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(currentUser, placeOrderRequest));
    }
}
