package de.fhdw.webshop.user;

import de.fhdw.webshop.discount.DiscountService;
import de.fhdw.webshop.discount.dto.CouponResponse;
import de.fhdw.webshop.discount.dto.DiscountResponse;
import de.fhdw.webshop.accountlink.AccountLinkService;
import de.fhdw.webshop.accountlink.dto.TeamBudgetResponse;
import de.fhdw.webshop.accountlink.dto.UpdateTeamBudgetRequest;
import de.fhdw.webshop.user.dto.*;
import de.fhdw.webshop.user.recentlyviewed.RecentlyViewedProductService;
import de.fhdw.webshop.user.recentlyviewed.dto.RecentlyViewedProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final DiscountService discountService;
    private final AccountLinkService accountLinkService;
    private final RecentlyViewedProductService recentlyViewedProductService;

    /** US #9 — Return own profile including customer number. */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(userService.getProfile(currentUser));
    }

    /** Returns the saved checkout defaults for address and payment method. */
    @GetMapping("/me/checkout-profile")
    public ResponseEntity<CheckoutProfileResponse> getCheckoutProfile(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(userService.getCheckoutProfile(currentUser));
    }

    /** US #4 — Change password. */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal User currentUser,
                                               @Valid @RequestBody ChangePasswordRequest changePasswordRequest) {
        userService.changePassword(currentUser, changePasswordRequest);
        return ResponseEntity.noContent().build();
    }

    /** US #5 — Change email address. */
    @PutMapping("/me/email")
    public ResponseEntity<Void> changeEmail(@AuthenticationPrincipal User currentUser,
                                            @Valid @RequestBody ChangeEmailRequest changeEmailRequest) {
        userService.changeEmail(currentUser, changeEmailRequest);
        return ResponseEntity.noContent().build();
    }

    /** US #325 — Customer enables or disables abandoned-cart reminder emails. */
    @PutMapping("/me/cart-reminders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<UserProfileResponse> updateCartReminderSettings(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CartReminderSettingsRequest request) {
        return ResponseEntity.ok(userService.updateCartReminderSettings(currentUser, request));
    }

    /** US #331 — Persist the preferred shop language for future sessions. */
    @PutMapping("/me/language")
    public ResponseEntity<UserProfileResponse> updatePreferredLanguage(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody PreferredLanguageRequest request) {
        return ResponseEntity.ok(userService.updatePreferredLanguage(currentUser, request));
    }

    /** US #7 — Deregister (soft-delete) own account. */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deactivateAccount(@AuthenticationPrincipal User currentUser) {
        userService.deactivateAccount(currentUser);
        return ResponseEntity.noContent().build();
    }

    /** US #45 — Save or replace delivery address. */
    @PutMapping("/me/delivery-address")
    public ResponseEntity<Void> saveDeliveryAddress(@AuthenticationPrincipal User currentUser,
                                                    @Valid @RequestBody DeliveryAddressRequest deliveryAddressRequest) {
        userService.saveDeliveryAddress(currentUser, deliveryAddressRequest);
        return ResponseEntity.noContent().build();
    }

    /** US #44 — Save or replace payment method. */
    @PutMapping("/me/payment-method")
    public ResponseEntity<Void> savePaymentMethod(@AuthenticationPrincipal User currentUser,
                                                  @Valid @RequestBody PaymentMethodRequest paymentMethodRequest) {
        userService.savePaymentMethod(currentUser, paymentMethodRequest);
        return ResponseEntity.noContent().build();
    }

    /** Customer views their own active discounts. */
    @GetMapping("/me/discounts")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<DiscountResponse>> getMyDiscounts(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(discountService.listDiscountsForCustomer(currentUser.getId()));
    }

    /** Customer views their own coupons. */
    @GetMapping("/me/coupons")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<CouponResponse>> getMyCoupons(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(discountService.listCouponsForCustomer(currentUser.getId()));
    }

    /** US #313 — Customer views their recently viewed products in the profile area. */
    @GetMapping("/me/recently-viewed")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<RecentlyViewedProductResponse>> getMyRecentlyViewedProducts(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) Boolean marketplace) {
        return ResponseEntity.ok(recentlyViewedProductService.listForUser(currentUser, marketplace));
    }

    /** US #313 — Record a product detail page visit for the current customer. */
    @PostMapping("/me/recently-viewed/{productId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> recordRecentlyViewedProduct(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long productId) {
        recentlyViewedProductService.recordView(currentUser, productId);
        return ResponseEntity.noContent().build();
    }

    /** Issue #220 — B2B administrators manage order limits for linked employee accounts. */
    @GetMapping("/me/team-budgets")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<TeamBudgetResponse>> getMyTeamBudgets(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(accountLinkService.listTeamBudgets(currentUser));
    }

    /** Issue #220 — Save a maximum order value limit; null or 0 means unlimited. */
    @PutMapping("/me/team-budgets/{linkedUserId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TeamBudgetResponse> updateMyTeamBudget(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long linkedUserId,
            @Valid @RequestBody UpdateTeamBudgetRequest request) {
        return ResponseEntity.ok(accountLinkService.updateTeamBudget(
                currentUser,
                linkedUserId,
                request.maxOrderValueLimit()));
    }
}
