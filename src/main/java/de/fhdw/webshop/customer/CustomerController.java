package de.fhdw.webshop.customer;

import de.fhdw.webshop.cart.CartService;
import de.fhdw.webshop.cart.dto.AddToCartRequest;
import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.discount.DiscountService;
import de.fhdw.webshop.discount.dto.CouponResponse;
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

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /** List all discounts for a customer. */
    @GetMapping("/{id}/discounts")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<DiscountResponse>> listDiscounts(@PathVariable Long id) {
        return ResponseEntity.ok(discountService.listDiscountsForCustomer(id));
    }

    /** Delete a specific discount for a customer. */
    @DeleteMapping("/{id}/discounts/{discountId}")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Void> deleteDiscount(@PathVariable Long id,
                                               @PathVariable Long discountId,
                                               @AuthenticationPrincipal User currentUser) {
        discountService.deleteDiscount(discountId, id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /** US #24 — Assign a coupon to a customer. */
    @PostMapping("/{id}/coupons")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<CouponResponse> createCoupon(@PathVariable Long id,
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
    public ResponseEntity<Void> deleteCoupon(@PathVariable Long id,
                                             @PathVariable Long couponId,
                                             @AuthenticationPrincipal User currentUser) {
        discountService.deleteCoupon(couponId, id, currentUser);
        return ResponseEntity.noContent().build();
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

    /**
     * 360°-Dashboard combining customer profile, cart, orders, discounts, coupons and revenue.
     * canViewSalesData / canManageSalesActions are true for SALES_EMPLOYEE and ADMIN.
     */
    @GetMapping("/{id}/dashboard")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<CustomerDashboardResponse> getCustomerDashboard(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @AuthenticationPrincipal User currentUser) {

        User customer = userService.loadById(id);
        String role = currentUser.getRole().name();
        boolean canViewSalesData = "SALES_EMPLOYEE".equals(role) || "ADMIN".equals(role);
        boolean canManageSalesActions = canViewSalesData;

        UserProfileResponse customerProfile = new UserProfileResponse(
                customer.getId(), customer.getUsername(), customer.getEmail(),
                customer.getRole(), customer.getUserType(), customer.getCustomerNumber());

        Optional<BusinessInfo> businessInfoOpt = businessInfoRepository.findByUserId(id);
        Map<String, Object> businessInfoMap = businessInfoOpt.map(bi -> Map.<String, Object>of(
                "companyName", bi.getCompanyName() != null ? bi.getCompanyName() : "",
                "industry", bi.getIndustry() != null ? bi.getIndustry() : "",
                "companySize", bi.getCompanySize() != null ? bi.getCompanySize() : ""
        )).orElse(null);

        CartResponse cart = cartService.getCart(id);

        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(90);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();

        RevenueStatisticsResponse revenue = canViewSalesData
                ? statisticsService.getCustomerRevenue(id, effectiveFrom, effectiveTo)
                : null;

        List<OrderResponse> allOrders = orderService.listOrdersForCustomer(id);
        List<OrderResponse> recentOrders = allOrders.stream().limit(5).toList();
        long totalOrderCount = allOrders.size();

        Instant latestOrderAt = allOrders.stream()
                .map(OrderResponse::createdAt)
                .max(Instant::compareTo)
                .orElse(null);

        List<DiscountResponse> discounts = canViewSalesData
                ? discountService.listDiscountsForCustomer(id)
                : List.of();

        List<CouponResponse> coupons = canViewSalesData
                ? discountService.listCouponsForCustomer(id)
                : List.of();

        List<String> alerts = buildAlerts(allOrders, totalOrderCount);
        String behaviorSummary = buildBehaviorSummary(totalOrderCount, latestOrderAt, revenue);

        CustomerDashboardResponse dashboard = new CustomerDashboardResponse(
                customerProfile,
                businessInfoMap,
                businessInfoOpt.isPresent(),
                cart,
                recentOrders,
                totalOrderCount,
                latestOrderAt,
                discounts,
                coupons,
                revenue,
                effectiveFrom,
                effectiveTo,
                canViewSalesData,
                canManageSalesActions,
                alerts,
                behaviorSummary
        );

        return ResponseEntity.ok(dashboard);
    }

    private List<String> buildAlerts(List<OrderResponse> orders, long totalOrderCount) {
        List<String> alerts = new ArrayList<>();
        if (totalOrderCount == 0) {
            alerts.add("Noch keine Bestellungen – Kunde ist neu oder inaktiv.");
        }
        long activeCoupons = orders.stream()
                .filter(o -> o.couponCode() != null && !o.couponCode().isBlank())
                .count();
        if (activeCoupons > 0) {
            alerts.add("Kunde hat bereits " + activeCoupons + " Coupon(s) eingelöst.");
        }
        return alerts;
    }

    private String buildBehaviorSummary(long totalOrderCount, Instant latestOrderAt,
                                        RevenueStatisticsResponse revenue) {
        if (totalOrderCount == 0) {
            return "Dieser Kunde hat noch keine Bestellungen aufgegeben.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Der Kunde hat bisher ").append(totalOrderCount).append(" Bestellung(en) aufgegeben");
        if (revenue != null && revenue.totalRevenue() != null) {
            sb.append(" mit einem Gesamtumsatz von ")
              .append(String.format("%.2f", revenue.totalRevenue())).append(" EUR im gewählten Zeitraum");
        }
        if (latestOrderAt != null) {
            LocalDate lastOrderDate = latestOrderAt.atZone(ZoneOffset.UTC).toLocalDate();
            long daysSince = java.time.temporal.ChronoUnit.DAYS.between(lastOrderDate, LocalDate.now());
            sb.append(". Letzter Kauf vor ").append(daysSince).append(" Tag(en).");
        }
        return sb.toString();
    }
}
