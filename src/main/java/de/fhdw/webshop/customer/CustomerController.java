package de.fhdw.webshop.customer;

import de.fhdw.webshop.cart.CartService;
import de.fhdw.webshop.cart.audit.CartChangeLogService;
import de.fhdw.webshop.cart.audit.dto.CartChangeLogResponse;
import de.fhdw.webshop.cart.dto.AddToCartRequest;
import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.cart.dto.UpdateCartItemQuantityRequest;
import de.fhdw.webshop.discount.DiscountService;
import de.fhdw.webshop.discount.dto.CouponResponse;
import de.fhdw.webshop.discount.dto.CreateCouponRequest;
import de.fhdw.webshop.discount.dto.CreateDiscountRequest;
import de.fhdw.webshop.discount.dto.DiscountResponse;
import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.notification.dto.SendEmailRequest;
import de.fhdw.webshop.order.OrderService;
import de.fhdw.webshop.order.dto.OrderResponse;
import de.fhdw.webshop.user.BusinessInfo;
import de.fhdw.webshop.user.BusinessInfoRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserService;
import de.fhdw.webshop.user.UserType;
import de.fhdw.webshop.user.dto.UserProfileResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final CartService cartService;
    private final CartChangeLogService cartChangeLogService;
    private final OrderService orderService;
    private final DiscountService discountService;
    private final BusinessInfoRepository businessInfoRepository;
    private final StatisticsService statisticsService;
    private final EmailService emailService;

    /** US #30, #32 - List all customers, optionally filtered by search term. */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<UserProfileResponse>> listCustomers(
            @RequestParam(required = false) String search) {
        String searchTerm = search == null ? "" : search.trim();
        Long searchId = parseIdSearch(searchTerm);
        List<User> matchingCustomers = searchId == null
                ? userRepository.findActiveCustomers(searchTerm, UserRole.CUSTOMER)
                : userRepository.findById(searchId)
                        .filter(user -> user.isActive() && user.getRoles().contains(UserRole.CUSTOMER))
                        .stream()
                        .toList();

        List<UserProfileResponse> customers = matchingCustomers.stream()
                .map(user -> new UserProfileResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getRoles(),
                        user.getUserType(),
                        user.getCustomerNumber(),
                        user.isActive(),
                        user.getAgbAcceptedAt()))
                .toList();
        return ResponseEntity.ok(customers);
    }

    private Long parseIdSearch(String searchTerm) {
        if (searchTerm == null || !searchTerm.matches("\\d+")) {
            return null;
        }

        try {
            return Long.parseLong(searchTerm);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** US #12 - Look up a customer by ID. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<UserProfileResponse> getCustomer(@PathVariable Long id) {
        User customer = userService.loadById(id);
        return ResponseEntity.ok(new UserProfileResponse(
                customer.getId(),
                customer.getUsername(),
                customer.getEmail(),
                customer.getRoles(),
                customer.getUserType(),
                customer.getCustomerNumber(),
                customer.isActive(),
                customer.getAgbAcceptedAt()));
    }

    /** US #11 - View a customer's cart on their behalf. */
    @GetMapping("/{id}/cart")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<CartResponse> getCustomerCart(@PathVariable Long id) {
        return ResponseEntity.ok(cartService.getCart(id));
    }

    /** US #21 - Add an item to a customer's cart on their behalf. */
    @PostMapping("/{id}/cart/items")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<CartResponse> addItemToCustomerCart(
            @PathVariable Long id,
            @Valid @RequestBody AddToCartRequest addToCartRequest,
            @AuthenticationPrincipal User currentUser) {
        User customer = userService.loadById(id);
        return ResponseEntity.ok(cartService.addItemForCustomerByEmployee(customer, addToCartRequest, currentUser));
    }

    /** US #253 - Update a customer's cart quantity on their behalf and log the employee action. */
    @PutMapping("/{id}/cart/items/{productId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<CartResponse> updateItemQuantityInCustomerCart(
            @PathVariable Long id,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request,
            @AuthenticationPrincipal User currentUser) {
        User customer = userService.loadById(id);
        return ResponseEntity.ok(cartService.updateItemQuantityForCustomerByEmployee(
                customer,
                productId,
                request.quantity(),
                currentUser));
    }

    /** US #22 - Remove an item from a customer's cart on their behalf. */
    @DeleteMapping("/{id}/cart/items/{productId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<CartResponse> removeItemFromCustomerCart(
            @PathVariable Long id,
            @PathVariable Long productId,
            @AuthenticationPrincipal User currentUser) {
        User customer = userService.loadById(id);
        return ResponseEntity.ok(cartService.removeItemForCustomerByEmployee(customer, productId, currentUser));
    }

    /** US #253 - Chronological cart-change timeline for customer cockpit and audit follow-up. */
    @GetMapping("/{id}/cart-audit")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<CartChangeLogResponse>> getCustomerCartAudit(@PathVariable Long id) {
        return ResponseEntity.ok(cartChangeLogService.listForCustomer(id));
    }

    /** US #27 - View a customer's order history. */
    @GetMapping("/{id}/orders")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<OrderResponse>> getCustomerOrders(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.listOrdersForCustomer(id));
    }

    /** US #33, #54 - Create a discount for a customer. */
    @PostMapping("/{id}/discounts")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<DiscountResponse> createDiscount(
            @PathVariable Long id,
            @Valid @RequestBody CreateDiscountRequest createDiscountRequest,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(discountService.createDiscount(id, createDiscountRequest, currentUser));
    }

    /** List all discounts for a customer. */
    @GetMapping("/{id}/discounts")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<DiscountResponse>> listDiscounts(@PathVariable Long id) {
        return ResponseEntity.ok(discountService.listDiscountsForCustomer(id));
    }

    /** Delete a specific discount for a customer. */
    @DeleteMapping("/{id}/discounts/{discountId}")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Void> deleteDiscount(
            @PathVariable Long id,
            @PathVariable Long discountId,
            @AuthenticationPrincipal User currentUser) {
        discountService.deleteDiscount(discountId, id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /** US #24 - Assign a coupon to a customer. */
    @PostMapping("/{id}/coupons")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<CouponResponse> createCoupon(
            @PathVariable Long id,
            @Valid @RequestBody CreateCouponRequest createCouponRequest,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(discountService.createCoupon(id, createCouponRequest, currentUser));
    }

    /** List all coupons for a customer. */
    @GetMapping("/{id}/coupons")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<CouponResponse>> listCoupons(@PathVariable Long id) {
        return ResponseEntity.ok(discountService.listCouponsForCustomer(id));
    }

    /** Delete a specific coupon for a customer. */
    @DeleteMapping("/{id}/coupons/{couponId}")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Void> deleteCoupon(
            @PathVariable Long id,
            @PathVariable Long couponId,
            @AuthenticationPrincipal User currentUser) {
        discountService.deleteCoupon(couponId, id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /** US #29 - Revenue statistics for a customer over a date range. */
    @GetMapping("/{id}/revenue")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<RevenueStatisticsResponse> getCustomerRevenue(
            @PathVariable Long id,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(statisticsService.getCustomerRevenue(id, from, to));
    }

    /** US #37 - Send an email to a customer. */
    @PostMapping("/{id}/email")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Void> sendEmailToCustomer(
            @PathVariable Long id,
            @Valid @RequestBody SendEmailRequest sendEmailRequest) {
        User customer = userService.loadById(id);
        emailService.sendEmailToCustomer(customer, sendEmailRequest.subject(), sendEmailRequest.body());
        return ResponseEntity.noContent().build();
    }

    /** US #43 - View business details for a business customer. */
    @GetMapping("/{id}/business-info")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<BusinessInfo> getBusinessInfo(@PathVariable Long id) {
        BusinessInfo businessInfo = businessInfoRepository.findByUserId(id)
                .orElseThrow(() -> new EntityNotFoundException("No business info found for customer: " + id));
        return ResponseEntity.ok(businessInfo);
    }

    /** 360-degree dashboard with profile, cart, orders, revenue, discounts and coupons. */
    @GetMapping("/{id}/dashboard")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<CustomerDashboardResponse> getCustomerDashboard(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @AuthenticationPrincipal User currentUser) {
        User customer = userService.loadById(id);
        boolean canViewSalesData = currentUser != null
                && (currentUser.hasRole(UserRole.SALES_EMPLOYEE) || currentUser.hasRole(UserRole.ADMIN));
        boolean canManageSalesActions = canViewSalesData;
        boolean businessCustomer = customer.getUserType() == UserType.BUSINESS;
        LocalDate resolvedTo = to != null ? to : LocalDate.now();
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(90);

        UserProfileResponse customerProfile = new UserProfileResponse(
                customer.getId(),
                customer.getUsername(),
                customer.getEmail(),
                customer.getRoles(),
                customer.getUserType(),
                customer.getCustomerNumber(),
                customer.isActive(),
                customer.getAgbAcceptedAt());

        CartResponse cart = cartService.getCart(id);
        BigDecimal cartTotal = cart != null && cart.total() != null ? cart.total() : BigDecimal.ZERO;
        long totalOrderCount = orderService.countOrdersForCustomer(id);
        Instant latestOrderAt = orderService.findLatestOrderTimestamp(id);

        List<OrderResponse> recentOrders = canViewSalesData
                ? orderService.listOrdersForCustomer(id).stream().limit(5).toList()
                : List.of();

        RevenueStatisticsResponse revenue = canViewSalesData
                ? statisticsService.getCustomerRevenue(id, resolvedFrom, resolvedTo)
                : null;

        List<DiscountResponse> allDiscounts = discountService.listDiscountsForCustomer(id);
        List<CouponResponse> allCoupons = discountService.listCouponsForCustomer(id);

        List<DiscountResponse> discounts = canViewSalesData ? allDiscounts : List.of();
        List<CouponResponse> coupons = canViewSalesData ? allCoupons : List.of();
        List<CartChangeLogResponse> cartChangeTimeline = cartChangeLogService.listForCustomer(id);

        Map<String, Object> businessInfo = canViewSalesData
                ? businessInfoRepository.findByUserId(id).map(this::toBusinessInfoMap).orElse(null)
                : null;

        List<String> alerts = buildAlerts(latestOrderAt, cartTotal, totalOrderCount, allDiscounts, allCoupons);
        String behaviorSummary = buildBehaviorSummary(
                businessCustomer,
                totalOrderCount,
                latestOrderAt,
                cartTotal,
                allDiscounts,
                allCoupons);

        return ResponseEntity.ok(new CustomerDashboardResponse(
                customerProfile,
                businessInfo,
                businessCustomer,
                cart,
                recentOrders,
                totalOrderCount,
                latestOrderAt,
                discounts,
                coupons,
                revenue,
                resolvedFrom,
                resolvedTo,
                canViewSalesData,
                canManageSalesActions,
                alerts,
                behaviorSummary,
                cartChangeTimeline));
    }

    private Map<String, Object> toBusinessInfoMap(BusinessInfo businessInfo) {
        return Map.<String, Object>of(
                "companyName", defaultString(businessInfo.getCompanyName()),
                "industry", defaultString(businessInfo.getIndustry()),
                "companySize", defaultString(businessInfo.getCompanySize()));
    }

    private String defaultString(String value) {
        return value != null ? value : "";
    }

    private List<String> buildAlerts(
            Instant latestOrderAt,
            BigDecimal cartTotal,
            long totalOrderCount,
            List<DiscountResponse> discounts,
            List<CouponResponse> coupons) {
        List<String> alerts = new ArrayList<>();
        Instant now = Instant.now();
        Instant ninetyDaysAgo = now.minus(90, ChronoUnit.DAYS);
        boolean hasActivePromotion = !discounts.isEmpty()
                || coupons.stream().anyMatch(coupon -> !coupon.used());

        if (latestOrderAt == null || latestOrderAt.isBefore(ninetyDaysAgo)) {
            alerts.add("Kein Kauf in den letzten 90 Tagen");
        }

        if (cartTotal.compareTo(new BigDecimal("500")) >= 0
                && (totalOrderCount == 0
                || latestOrderAt == null
                || latestOrderAt.isBefore(now.minus(30, ChronoUnit.DAYS)))) {
            alerts.add("Hoher Warenkorb, aber keine aktuelle Bestellung");
        }

        if (hasActivePromotion) {
            alerts.add("Aktiver Rabatt oder Coupon vorhanden");
        }

        return alerts;
    }

    private String buildBehaviorSummary(
            boolean businessCustomer,
            long totalOrderCount,
            Instant latestOrderAt,
            BigDecimal cartTotal,
            List<DiscountResponse> discounts,
            List<CouponResponse> coupons) {
        boolean hasActivePromotion = !discounts.isEmpty()
                || coupons.stream().anyMatch(coupon -> !coupon.used());

        if (totalOrderCount == 0) {
            if (cartTotal.compareTo(BigDecimal.ZERO) > 0) {
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
            return "Es laufen bereits Vertriebsmassnahmen. Jetzt ist ein guter Zeitpunkt fuer eine konkrete Nachfassaktion.";
        }

        return "Der Kunde zeigt ein stabiles Kaufverhalten und kann gezielt mit passenden Artikeln oder Serviceangeboten angesprochen werden.";
    }
}
