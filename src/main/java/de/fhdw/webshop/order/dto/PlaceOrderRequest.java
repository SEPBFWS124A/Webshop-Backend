package de.fhdw.webshop.order.dto;

import de.fhdw.webshop.order.ShippingMethod;
import de.fhdw.webshop.user.dto.DeliveryAddressRequest;
import de.fhdw.webshop.user.dto.PaymentMethodRequest;
import jakarta.validation.Valid;

import java.util.List;

public record PlaceOrderRequest(
        String couponCode,
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
        List<@Valid PlaceOrderItemRequest> items
) {}
