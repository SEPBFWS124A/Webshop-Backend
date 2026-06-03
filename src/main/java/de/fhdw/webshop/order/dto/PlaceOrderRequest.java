package de.fhdw.webshop.order.dto;

import de.fhdw.webshop.order.ShippingMethod;
import de.fhdw.webshop.user.dto.DeliveryAddressRequest;
import de.fhdw.webshop.user.dto.PaymentMethodRequest;
import jakarta.validation.Valid;

import java.util.List;

public record PlaceOrderRequest(
        String couponCode,
        String couponCode2,
        String email,
        String customerName,
        String customerSalutation,
        String previewOrderNumber,
        @Valid DeliveryAddressRequest deliveryAddress,
        ShippingMethod shippingMethod,
        @Valid PaymentMethodRequest paymentMethod,
        Boolean allowUnverifiedAddress,
        Boolean acceptedTermsAndConditions,
        Boolean acceptedPrivacyPolicy,
        Boolean saveDeliveryAddress,
        Boolean savePaymentMethod,
        Boolean carbonCompensationSelected,
        Long pickupStoreId,
        String approvalReason,
        List<@Valid PlaceOrderItemRequest> items,
        String affiliateCode,
        Boolean restrictionVerified,
        String restrictionVerificationReference
) {}
