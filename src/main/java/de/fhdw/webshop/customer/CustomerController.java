package de.fhdw.webshop.customer;

import de.fhdw.webshop.cart.CartService;
import de.fhdw.webshop.cart.dto.AddToCartRequest;
import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.discount.DiscountService;
import de.fhdw.webshop.discount.dto.CreateCouponRequest;
import de.fhdw.webshop.discount.dto.CreateDiscountRequest;
import de.fhdw.webshop.discount.dto.DiscountResponse;
import de.fhdw.webshop.order.OrderService;
import de.fhdw.webshop.order.dto.OrderResponse;
import de.fhdw.webshop.user.BusinessInfo;
import de.fhdw.webshop.user.BusinessInfoRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserService;
import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.notification.dto.SendEmailRequest;
import de.fhdw.webshop.user.dto.UserProfileResponse;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final CartService cartService;
    private final OrderService orderService;
    private final DiscountService discountService;
    private final BusinessInfoRepository businessInfoRepository;
    private final StatisticsService statisticsService;
    private final EmailService emailService;

    /** US #30, #32 — List all customers, optionally filtered by search term. */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<UserProfileResponse>> listCustomers(
            @RequestParam(required = false) String search) {
        List<UserProfileResponse> customers = userRepository.findActiveCustomers(search == null ? "" : search).stream()
                .map(user -> new UserProfileResponse(
                        user.getId(), user.getUsername(), user.getEmail(),
                        user.getRole(), user.getUserType(), user.getCustomerNumber()))
                .toList();
        return ResponseEntity.ok(customers);
    }

    /** US #12 — Look up a customer by ID (includes customer number). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<UserProfileResponse> getCustomer(@PathVariable Long id) {
        User customer = userService.loadById(id);
        return ResponseEntity.ok(new UserProfileResponse(
                customer.getId(), customer.getUsername(), customer.getEmail(),
                customer.getRole(), customer.getUserType(), customer.getCustomerNumber()));
    }

    /** US #11 — View a customer's cart on their behalf. */
    @GetMapping("/{id}/cart")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<CartResponse> getCustomerCart(@PathVariable Long id) {
        return ResponseEntity.ok(cartService.getCart(id));
    }

    /** US #21 — Add an item to a customer's cart on their behalf. */
    @PostMapping("/{id}/cart/items")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<CartResponse> addItemToCustomerCart(@PathVariable Long id,
                                                              @Valid @RequestBody AddToCartRequest addToCartRequest) {
        User customer = userService.loadById(id);
        return ResponseEntity.ok(cartService.addItem(customer, addToCartRequest));
    }

    /** US #22 — Remove an item from a customer's cart on their behalf. */
    @DeleteMapping("/{id}/cart/items/{productId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<CartResponse> removeItemFromCustomerCart(@PathVariable Long id,
                                                                   @PathVariable Long productId) {
        User customer = userService.loadById(id);
        return ResponseEntity.ok(cartService.removeItem(customer, productId));
    }

    /** US #27 — View a customer's order history (sales employee). */
    @GetMapping("/{id}/orders")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<OrderResponse>> getCustomerOrders(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.listOrdersForCustomer(id));
    }

    /** US #33, #54 — Create a discount for a customer (time-limited or permanent). */
    @PostMapping("/{id}/discounts")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<DiscountResponse> createDiscount(@PathVariable Long id,
                                                           @Valid @RequestBody CreateDiscountRequest createDiscountRequest,
                                                           @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(discountService.createDiscount(id, createDiscountRequest, currentUser));
    }

    /** US #24 — Assign a coupon to a customer. */
    @PostMapping("/{id}/coupons")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Void> createCoupon(@PathVariable Long id,
                                             @Valid @RequestBody CreateCouponRequest createCouponRequest,
                                             @AuthenticationPrincipal User currentUser) {
        discountService.createCoupon(id, createCouponRequest, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** US #29 — Revenue statistics for a customer over a date range. */
    @GetMapping("/{id}/revenue")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<RevenueStatisticsResponse> getCustomerRevenue(
            @PathVariable Long id,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(statisticsService.getCustomerRevenue(id, from, to));
    }

    /** US #37 — Send an email to a customer (delegates to EmailService). */
    @PostMapping("/{id}/email")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Void> sendEmailToCustomer(@PathVariable Long id,
                                                    @Valid @RequestBody SendEmailRequest sendEmailRequest,
                                                    @AuthenticationPrincipal User currentUser) {
        User customer = userService.loadById(id);
        emailService.sendEmailToCustomer(customer, sendEmailRequest.subject(), sendEmailRequest.body());
        return ResponseEntity.noContent().build();
    }

    /** US #43 — View business details for a business customer. */
    @GetMapping("/{id}/business-info")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<BusinessInfo> getBusinessInfo(@PathVariable Long id) {
        BusinessInfo businessInfo = businessInfoRepository.findByUserId(id)
                .orElseThrow(() -> new EntityNotFoundException("No business info found for customer: " + id));
        return ResponseEntity.ok(businessInfo);
    }
}
