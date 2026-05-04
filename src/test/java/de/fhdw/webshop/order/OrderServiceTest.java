package de.fhdw.webshop.order;

import de.fhdw.webshop.accountlink.AccountLink;
import de.fhdw.webshop.accountlink.AccountLinkRepository;
import de.fhdw.webshop.address.AddressLookupService;
import de.fhdw.webshop.address.AddressValidationResponse;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.cart.CartItem;
import de.fhdw.webshop.cart.CartRepository;
import de.fhdw.webshop.cart.CartService;
import de.fhdw.webshop.discount.CouponRepository;
import de.fhdw.webshop.order.dto.OrderPreviewResponse;
import de.fhdw.webshop.order.dto.OrderResponse;
import de.fhdw.webshop.order.dto.PlaceOrderRequest;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.user.DeliveryAddressRepository;
import de.fhdw.webshop.user.PaymentMethodRepository;
import de.fhdw.webshop.user.PaymentMethodType;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserService;
import de.fhdw.webshop.user.UserType;
import de.fhdw.webshop.user.dto.DeliveryAddressRequest;
import de.fhdw.webshop.user.dto.PaymentMethodRequest;
import de.fhdw.webshop.notification.EmailService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    @Test
    void previewMarksApprovalRequiredWhenBudgetLimitIsExceeded() {
        TestContext context = newContext(new BigDecimal("100.00"));

        OrderPreviewResponse preview = context.service.previewOrder(context.customer, request(null));

        assertThat(preview.approvalRequired()).isTrue();
        assertThat(preview.approvalBudgetLimit()).isEqualByComparingTo("100.00");
        assertThat(preview.totalPrice()).isGreaterThan(preview.approvalBudgetLimit());
    }

    @Test
    void placeOrderCreatesPendingApprovalRequestWithoutFinalOrderEffects() {
        TestContext context = newContext(new BigDecimal("100.00"));
        when(context.orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = context.service.placeOrder(context.customer, request("Bitte fuer das Projekt freigeben"));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(context.orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(response.status()).isEqualTo(OrderStatus.Pending_Approval);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.Pending_Approval);
        assertThat(savedOrder.getApprovalReason()).isEqualTo("Bitte fuer das Projekt freigeben");
        assertThat(savedOrder.getApprovalBudgetLimit()).isEqualByComparingTo("100.00");
        assertThat(savedOrder.getItems()).hasSize(1);
        assertThat(context.product.getStock()).isEqualTo(5);
        verify(context.cartService).clearCartSilently(context.customer.getId());
        verify(context.productRepository, never()).saveAll(any());
        verify(context.emailService, never()).sendEmail(any(), any(), any());
    }

    private static TestContext newContext(BigDecimal budgetLimit) {
        OrderRepository orderRepository = mock(OrderRepository.class);
        CartRepository cartRepository = mock(CartRepository.class);
        CartService cartService = mock(CartService.class);
        CouponRepository couponRepository = mock(CouponRepository.class);
        ProductService.DiscountLookupPort discountLookupPort = mock(ProductService.DiscountLookupPort.class);
        ProductService productService = mock(ProductService.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        DeliveryAddressRepository deliveryAddressRepository = mock(DeliveryAddressRepository.class);
        PaymentMethodRepository paymentMethodRepository = mock(PaymentMethodRepository.class);
        UserService userService = mock(UserService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        EmailService emailService = mock(EmailService.class);
        AddressLookupService addressLookupService = mock(AddressLookupService.class);
        AccountLinkRepository accountLinkRepository = mock(AccountLinkRepository.class);

        OrderService service = new OrderService(
                orderRepository,
                cartRepository,
                cartService,
                couponRepository,
                discountLookupPort,
                productService,
                productRepository,
                deliveryAddressRepository,
                paymentMethodRepository,
                userService,
                auditLogService,
                emailService,
                addressLookupService,
                accountLinkRepository);

        User customer = businessCustomer(10L, "employee");
        User manager = businessCustomer(11L, "manager");
        Product product = product();
        CartItem cartItem = new CartItem();
        cartItem.setUser(customer);
        cartItem.setProduct(product);
        cartItem.setQuantity(1);
        AccountLink link = new AccountLink();
        link.setUserA(customer);
        link.setUserB(manager);
        link.setMaxOrderValueLimit(budgetLimit);

        when(cartRepository.findByUserId(customer.getId())).thenReturn(List.of(cartItem));
        when(accountLinkRepository.findAllForUserId(customer.getId())).thenReturn(List.of(link));
        when(addressLookupService.validateAddress(any())).thenReturn(new AddressValidationResponse(
                true,
                "Hauptstrasse 1, 33602 Bielefeld, Germany",
                "Hauptstrasse 1",
                "33602",
                "Bielefeld",
                "Germany",
                null));

        return new TestContext(service, customer, product, orderRepository, cartService, productRepository, emailService);
    }

    private static PlaceOrderRequest request(String approvalReason) {
        return new PlaceOrderRequest(
                null,
                "employee@example.test",
                "Employee Buyer",
                "Keine Angabe",
                "ORD-APPROVAL-TEST",
                new DeliveryAddressRequest("Hauptstrasse 1", "Bielefeld", "33602", "Germany"),
                ShippingMethod.STANDARD,
                new PaymentMethodRequest(PaymentMethodType.BANK_TRANSFER, "Rechnung", null, null, null, null),
                false,
                true,
                true,
                false,
                false,
                false,
                approvalReason,
                null);
    }

    private static User businessCustomer(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash("hash");
        user.setUserType(UserType.BUSINESS);
        user.getRoles().add(UserRole.CUSTOMER);
        user.setActive(true);
        return user;
    }

    private static Product product() {
        Product product = new Product();
        product.setId(20L);
        product.setName("Budget Test Produkt");
        product.setRecommendedRetailPrice(new BigDecimal("200.00"));
        product.setStock(5);
        product.setPurchasable(true);
        return product;
    }

    private record TestContext(
            OrderService service,
            User customer,
            Product product,
            OrderRepository orderRepository,
            CartService cartService,
            ProductRepository productRepository,
            EmailService emailService
    ) {}
}
