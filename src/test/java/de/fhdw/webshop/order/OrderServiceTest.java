package de.fhdw.webshop.order;

import de.fhdw.webshop.accountlink.AccountLink;
import de.fhdw.webshop.accountlink.AccountLinkRepository;
import de.fhdw.webshop.address.AddressLookupService;
import de.fhdw.webshop.address.AddressValidationResponse;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.agb.AgbService;
import de.fhdw.webshop.user.UserRepository;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        ArgumentCaptor<String> addressCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(context.emailService).sendEmail(addressCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture());
        assertThat(addressCaptor.getValue()).isEqualTo(context.manager.getEmail());
        assertThat(subjectCaptor.getValue()).contains("Freigabe erforderlich", savedOrder.getOrderNumber());
        assertThat(bodyCaptor.getValue()).contains("Link zur Anfrage", "/profile", savedOrder.getOrderNumber());
    }

    @Test
    void approveApprovalRequestConfirmsOrderAndAppliesFinalOrderEffects() {
        TestContext context = newContext(new BigDecimal("100.00"));
        Order pendingOrder = pendingApprovalOrder(context.customer, context.product);
        when(context.orderRepository.findApprovalRequestForManager(pendingOrder.getId(), context.manager.getId()))
                .thenReturn(Optional.of(pendingOrder));
        when(context.orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.emailService.sendEmail(any(), any(), any())).thenReturn(true);

        OrderResponse regularResponse = context.service.placeOrder(context.customer, request("Projektbedarf"));
        assertThat(regularResponse.status()).isEqualTo(OrderStatus.Pending_Approval);

        var approvalResponse = context.service.approveApprovalRequest(context.manager, pendingOrder.getId());

        assertThat(approvalResponse.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(approvalResponse.confirmationEmailSent()).isTrue();
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(pendingOrder.getApprovalDecidedBy()).isEqualTo(context.manager);
        assertThat(pendingOrder.getApprovalDecidedAt()).isNotNull();
        assertThat(context.product.getStock()).isEqualTo(4);
        verify(context.productRepository).saveAll(List.of(context.product));
        verify(context.emailService, times(2)).sendEmail(any(), any(), any());
    }

    @Test
    void rejectApprovalRequestSendsReasonToEmployee() {
        TestContext context = newContext(new BigDecimal("100.00"));
        Order pendingOrder = pendingApprovalOrder(context.customer, context.product);
        when(context.orderRepository.findApprovalRequestForManager(pendingOrder.getId(), context.manager.getId()))
                .thenReturn(Optional.of(pendingOrder));
        when(context.orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var approvalResponse = context.service.rejectApprovalRequest(
                context.manager,
                pendingOrder.getId(),
                "Budget fuer dieses Quartal ausgeschoepft");

        assertThat(approvalResponse.status()).isEqualTo(OrderStatus.Rejected);
        assertThat(approvalResponse.rejectionReason()).isEqualTo("Budget fuer dieses Quartal ausgeschoepft");

        ArgumentCaptor<String> addressCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(context.emailService).sendEmail(addressCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture());
        assertThat(addressCaptor.getValue()).isEqualTo(context.customer.getEmail());
        assertThat(subjectCaptor.getValue()).contains("Freigabe abgelehnt", pendingOrder.getOrderNumber());
        assertThat(bodyCaptor.getValue()).contains("Budget fuer dieses Quartal ausgeschoepft", pendingOrder.getOrderNumber());
    }

    @Test
    void rejectApprovalRequestRequiresReason() {
        TestContext context = newContext(new BigDecimal("100.00"));
        Order pendingOrder = pendingApprovalOrder(context.customer, context.product);
        when(context.orderRepository.findApprovalRequestForManager(pendingOrder.getId(), context.manager.getId()))
                .thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> context.service.rejectApprovalRequest(context.manager, pendingOrder.getId(), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ablehnungsgrund");

        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.Pending_Approval);
        verify(context.orderRepository, never()).save(pendingOrder);
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
        UserRepository userRepository = mock(UserRepository.class);
        AgbService agbService = mock(AgbService.class);
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
                userRepository,
                agbService,
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

        return new TestContext(service, customer, manager, product, orderRepository, cartService, productRepository, emailService);
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

    private static Order pendingApprovalOrder(User customer, Product product) {
        Order order = new Order();
        order.setId(500L);
        order.setCustomer(customer);
        order.setOrderNumber("ORD-APPROVAL-500");
        order.setCustomerEmail(customer.getEmail());
        order.setCustomerName(customer.getUsername());
        order.setDeliveryStreet("Hauptstrasse 1");
        order.setDeliveryCity("Bielefeld");
        order.setDeliveryPostalCode("33602");
        order.setDeliveryCountry("Germany");
        order.setPaymentMethodType(PaymentMethodType.BANK_TRANSFER);
        order.setPaymentMaskedDetails("Rechnung");
        order.setTotalPrice(new BigDecimal("238.00"));
        order.setTaxAmount(new BigDecimal("38.00"));
        order.setShippingCost(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setStatus(OrderStatus.Pending_Approval);
        order.setApprovalReason("Projektbedarf");
        order.setApprovalBudgetLimit(new BigDecimal("100.00"));

        OrderItem item = new OrderItem();
        item.setId(501L);
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setPriceAtOrderTime(new BigDecimal("200.00"));
        order.getItems().add(item);
        return order;
    }

    private record TestContext(
            OrderService service,
            User customer,
            User manager,
            Product product,
            OrderRepository orderRepository,
            CartService cartService,
            ProductRepository productRepository,
            EmailService emailService
    ) {}
}
