package de.fhdw.webshop.user;

import de.fhdw.webshop.accountlink.AccountLinkService;
import de.fhdw.webshop.user.dto.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountLinkService accountLinkService;

    public UserProfileResponse getProfile(User currentUser) {
        return toProfileResponse(currentUser);
    }

    public CheckoutProfileResponse getCheckoutProfile(User currentUser) {
        DeliveryAddressResponse deliveryAddress = deliveryAddressRepository.findFirstByUserId(currentUser.getId())
                .map(this::toDeliveryAddressResponse)
                .orElse(null);
        PaymentMethodResponse paymentMethod = paymentMethodRepository.findFirstByUserId(currentUser.getId())
                .map(this::toPaymentMethodResponse)
                .orElse(null);
        return new CheckoutProfileResponse(deliveryAddress, paymentMethod);
    }

    /** US #4 — Change password after verifying the current one. */
    @Transactional
    public void changePassword(User currentUser, ChangePasswordRequest changePasswordRequest) {
        if (!passwordEncoder.matches(changePasswordRequest.currentPassword(), currentUser.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        currentUser.setPasswordHash(passwordEncoder.encode(changePasswordRequest.newPassword()));
        userRepository.save(currentUser);
    }

    /** US #5 — Update email address. */
    @Transactional
    public void changeEmail(User currentUser, ChangeEmailRequest changeEmailRequest) {
        if (userRepository.existsByEmailAndActiveTrue(changeEmailRequest.newEmail())) {
            throw new IllegalArgumentException("Email is already in use: " + changeEmailRequest.newEmail());
        }
        currentUser.setEmail(changeEmailRequest.newEmail());
        userRepository.save(currentUser);
    }

    /** US #7 — Soft-delete the account: deactivate and anonymize credentials to free unique constraints. */
    @Transactional
    public void deactivateAccount(User currentUser) {
        accountLinkService.removeLinksForUser(currentUser.getId());
        currentUser.setActive(false);
        currentUser.setUsername("deleted_" + currentUser.getId());
        currentUser.setEmail("deleted_" + currentUser.getId() + "@deleted.invalid");
        userRepository.save(currentUser);
    }

    /** US #45 — Save or replace the delivery address. */
    @Transactional
    public void saveDeliveryAddress(User currentUser, DeliveryAddressRequest deliveryAddressRequest) {
        DeliveryAddress deliveryAddress = deliveryAddressRepository
                .findFirstByUserId(currentUser.getId())
                .orElseGet(() -> {
                    DeliveryAddress newDeliveryAddress = new DeliveryAddress();
                    newDeliveryAddress.setUser(currentUser);
                    return newDeliveryAddress;
                });
        deliveryAddress.setStreet(deliveryAddressRequest.street());
        deliveryAddress.setCity(deliveryAddressRequest.city());
        deliveryAddress.setPostalCode(deliveryAddressRequest.postalCode());
        deliveryAddress.setCountry(deliveryAddressRequest.country());
        deliveryAddressRepository.save(deliveryAddress);
    }

    /** US #44 — Save or replace the preferred payment method. */
    @Transactional
    public void savePaymentMethod(User currentUser, PaymentMethodRequest paymentMethodRequest) {
        String storedDetails = PaymentMethodSupport.toStoredDetails(paymentMethodRequest);
        PaymentMethod paymentMethod = paymentMethodRepository
                .findFirstByUserId(currentUser.getId())
                .orElseGet(() -> {
                    PaymentMethod newPaymentMethod = new PaymentMethod();
                    newPaymentMethod.setUser(currentUser);
                    return newPaymentMethod;
                });
        paymentMethod.setMethodType(paymentMethodRequest.methodType());
        paymentMethod.setMaskedDetails(storedDetails);
        paymentMethodRepository.save(paymentMethod);
    }

    public User loadById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles(),
                user.getUserType(),
                user.getCustomerNumber()
        );
    }

    private DeliveryAddressResponse toDeliveryAddressResponse(DeliveryAddress deliveryAddress) {
        return new DeliveryAddressResponse(
                deliveryAddress.getStreet(),
                deliveryAddress.getCity(),
                deliveryAddress.getPostalCode(),
                deliveryAddress.getCountry()
        );
    }

    private PaymentMethodResponse toPaymentMethodResponse(PaymentMethod paymentMethod) {
        return new PaymentMethodResponse(paymentMethod.getMethodType(), paymentMethod.getMaskedDetails());
    }
}
