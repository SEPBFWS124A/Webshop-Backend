package de.fhdw.webshop.user;

import de.fhdw.webshop.user.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** US #9 — Return own profile including customer number. */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(userService.getProfile(currentUser));
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
}
