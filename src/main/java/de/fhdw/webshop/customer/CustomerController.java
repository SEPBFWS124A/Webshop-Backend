package de.fhdw.webshop.customer;

import de.fhdw.webshop.cart.CartService;
import de.fhdw.webshop.cart.dto.AddToCartRequest;
import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.discount.DiscountService;
import de.fhdw.webshop.discount.Coupon;
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
import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserType;
import de.fhdw.webshop.user.dto.UserProfileResponse;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
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

    /** Customer cockpit â€” aggregated 360-degree customer view for employees and sales staff. */
    @GetMapping("/{id}/dashboard")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<CustomerCockpitResponse> getCustomerDashboard(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            Authentication authentication) {
        User customer = userService.loadById(id);
        UserProfileResponse customerProfile = new UserProfileResponse(
                customer.getId(), customer.getUsername(), customer.getEmail(),
                customer.getRole(), customer.getUserType(), customer.getCustomerNumber());

        boolean canViewSalesData = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_SALES_EMPLOYEE")
                                || authority.getAuthority().equals("ROLE_ADMIN"));
        LocalDate resolvedTo = to != null ? to : LocalDate.now();
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(90);
        boolean businessCustomer = customer.getUserType() == UserType.BUSINESS;

        CartResponse cart = cartService.getCart(id);
        long totalOrderCount = orderService.countOrdersForCustomer(id);
        Instant latestOrderAt = orderService.findLatestOrderTimestamp(id);
        List<DiscountResponse> allDiscounts = discountService.listDiscountsForCustomer(id);
        List<CustomerCouponResponse> allCoupons = discountService.listCouponsForCustomer(id).stream()
                .map(this::toCustomerCouponResponse)
                .toList();

        CustomerBusinessInfoResponse businessInfo = canViewSalesData
                ? businessInfoRepository.findByUserId(id).map(this::toBusinessInfoResponse).orElse(null)
                : null;

        List<OrderResponse> recentOrders = canViewSalesData
                ? orderService.listOrdersForCustomer(id).stream().limit(5).toList()
                : List.of();

        RevenueStatisticsResponse revenue = canViewSalesData
                ? statisticsService.getCustomerRevenue(id, resolvedFrom, resolvedTo)
                : null;

        List<DiscountResponse> discounts = canViewSalesData ? allDiscounts : List.of();

        List<CustomerCouponResponse> coupons = canViewSalesData ? allCoupons : List.of();

        List<String> alerts = buildAlerts(
                latestOrderAt,
                cart.total(),
                totalOrderCount,
                allDiscounts,
                allCoupons
        );
        String behaviorSummary = buildBehaviorSummary(
                businessCustomer,
                totalOrderCount,
                latestOrderAt,
                cart.total(),
                allDiscounts,
                allCoupons
        );

        return ResponseEntity.ok(new CustomerCockpitResponse(
                customerProfile,
                businessInfo,
                cart,
                recentOrders,
                revenue,
                discounts,
                coupons,
                behaviorSummary,
                alerts,
                businessCustomer,
                totalOrderCount,
                latestOrderAt,
                resolvedFrom,
                resolvedTo,
                canViewSalesData,
                canViewSalesData
        ));
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

    private CustomerBusinessInfoResponse toBusinessInfoResponse(BusinessInfo businessInfo) {
        return new CustomerBusinessInfoResponse(
                businessInfo.getCompanyName(),
                businessInfo.getIndustry(),
                businessInfo.getCompanySize()
        );
    }

    private CustomerCouponResponse toCustomerCouponResponse(Coupon coupon) {
        return new CustomerCouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDiscountPercent(),
                coupon.getValidUntil(),
                coupon.isUsed()
        );
    }

    private List<String> buildAlerts(Instant latestOrderAt,
                                     BigDecimal cartTotal,
                                     long totalOrderCount,
                                     List<DiscountResponse> discounts,
                                     List<CustomerCouponResponse> coupons) {
        List<String> alerts = new ArrayList<>();
        Instant ninetyDaysAgo = Instant.now().minus(90, ChronoUnit.DAYS);
        BigDecimal safeCartTotal = cartTotal != null ? cartTotal : BigDecimal.ZERO;
        boolean hasActivePromotion = !discounts.isEmpty() || coupons.stream().anyMatch(coupon -> !coupon.used());

        if (latestOrderAt == null || latestOrderAt.isBefore(ninetyDaysAgo)) {
            alerts.add("Kein Kauf in den letzten 90 Tagen");
        }

        if (safeCartTotal.compareTo(new BigDecimal("500")) >= 0
                && (totalOrderCount == 0 || latestOrderAt == null || latestOrderAt.isBefore(Instant.now().minus(30, ChronoUnit.DAYS)))) {
            alerts.add("Hoher Warenkorb, aber keine aktuelle Bestellung");
        }

        if (hasActivePromotion) {
            alerts.add("Aktiver Rabatt oder Coupon vorhanden");
        }

        return alerts;
    }

    private String buildBehaviorSummary(boolean businessCustomer,
                                        long totalOrderCount,
                                        Instant latestOrderAt,
                                        BigDecimal cartTotal,
                                        List<DiscountResponse> discounts,
                                        List<CustomerCouponResponse> coupons) {
        BigDecimal safeCartTotal = cartTotal != null ? cartTotal : BigDecimal.ZERO;
        boolean hasActivePromotion = !discounts.isEmpty() || coupons.stream().anyMatch(coupon -> !coupon.used());

        if (totalOrderCount == 0) {
            if (safeCartTotal.compareTo(BigDecimal.ZERO) > 0) {
                return "Es gibt noch keine abgeschlossene Bestellung, aber bereits eine aktive Warenkorb-Absicht.";
            }
            return "Bisher liegt noch kein Kauf vor. Hier lohnt sich ein klarer Erstkauf-Impuls.";
        }

        if (latestOrderAt != null && latestOrderAt.isBefore(Instant.now().minus(90, ChronoUnit.DAYS))) {
            return "Der Kunde war frueher aktiv, hat aber seit ueber 90 Tagen keine Bestellung mehr abgeschlossen.";
        }

        if (businessCustomer) {
            return "Unternehmenskunde mit bestehender Kaufhistorie. Beratung sollte auf Business-Kontext und Folgebedarf ausgerichtet sein.";
        }

        if (hasActivePromotion) {
            return "Es laufen bereits Vertriebsmaßnahmen. Jetzt ist ein guter Zeitpunkt fuer eine konkrete Nachfassaktion.";
        }

        return "Der Kunde zeigt ein stabiles Kaufverhalten und kann gezielt mit passenden Artikeln oder Serviceangeboten angesprochen werden.";
    }
}
