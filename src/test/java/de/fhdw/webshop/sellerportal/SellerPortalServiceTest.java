package de.fhdw.webshop.sellerportal;

import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.marketplacedispute.MarketplaceDisputeRepository;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.returnrequest.ReturnRequest;
import de.fhdw.webshop.returnrequest.ReturnRequestItem;
import de.fhdw.webshop.returnrequest.ReturnRequestStatus;
import de.fhdw.webshop.sellerportal.dto.SellerDashboardResponse;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerPortalServiceTest {

    @Test
    void dashboardAggregatesDeliveredSalesAndOpenReturnHoldbacks() {
        SellerProfileRepository sellerProfileRepository = mock(SellerProfileRepository.class);
        SellerPayoutRepository sellerPayoutRepository = mock(SellerPayoutRepository.class);
        SellerPayoutItemRepository sellerPayoutItemRepository = mock(SellerPayoutItemRepository.class);
        SellerPortalOrderItemRepository sellerPortalOrderItemRepository = mock(SellerPortalOrderItemRepository.class);
        SellerPortalReturnRequestItemRepository sellerPortalReturnRequestItemRepository = mock(SellerPortalReturnRequestItemRepository.class);
        MarketplaceDisputeRepository marketplaceDisputeRepository = mock(MarketplaceDisputeRepository.class);

        SellerPortalService service = new SellerPortalService(
                sellerProfileRepository,
                sellerPayoutRepository,
                sellerPayoutItemRepository,
                sellerPortalOrderItemRepository,
                sellerPortalReturnRequestItemRepository,
                marketplaceDisputeRepository,
                mock(AuditLogService.class));

        User seller = sellerUser();
        SellerProfile profile = sellerProfile(seller);
        Order order = deliveredOrder();
        OrderItem orderItem = orderItem(order, profile.getDisplayName());
        order.getItems().add(orderItem);

        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setId(700L);
        returnRequest.setOrder(order);
        returnRequest.setCustomer(order.getCustomer());
        returnRequest.setStatus(ReturnRequestStatus.SUBMITTED);
        returnRequest.setCreatedAt(Instant.parse("2026-05-20T11:00:00Z"));

        ReturnRequestItem returnItem = new ReturnRequestItem();
        returnItem.setId(710L);
        returnItem.setReturnRequest(returnRequest);
        returnItem.setOrderItem(orderItem);
        returnItem.setProductName(orderItem.getProduct().getName());
        returnItem.setQuantity(1);

        List<SellerPayout> storedPayouts = new ArrayList<>();
        when(sellerProfileRepository.findByUserId(seller.getId())).thenReturn(Optional.of(profile));
        when(sellerPortalOrderItemRepository.findBySellerNameIgnoreCaseOrderByOrderCreatedAtDesc(profile.getDisplayName()))
                .thenReturn(List.of(orderItem));
        when(sellerPortalReturnRequestItemRepository.findBySellerName(profile.getDisplayName()))
                .thenReturn(List.of(returnItem));
        when(marketplaceDisputeRepository.findBySellerNameIgnoreCaseAndStatusIn(any(), any()))
                .thenReturn(List.of());
        when(sellerPayoutRepository.findBySellerProfileIdOrderByPeriodStartDesc(profile.getId()))
                .thenAnswer(invocation -> storedPayouts.stream()
                        .sorted((left, right) -> right.getPeriodStart().compareTo(left.getPeriodStart()))
                        .toList());
        when(sellerPayoutRepository.save(any(SellerPayout.class))).thenAnswer(invocation -> {
            SellerPayout payout = invocation.getArgument(0);
            if (payout.getId() == null) {
                payout.setId(900L);
            }
            storedPayouts.removeIf(existing -> existing.getId().equals(payout.getId()));
            storedPayouts.add(payout);
            return payout;
        });
        doAnswer(invocation -> null).when(sellerPayoutItemRepository).deleteByPayoutId(anyLong());
        when(sellerPayoutItemRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<SellerPayoutItem> items = invocation.getArgument(0);
            List<SellerPayoutItem> saved = new ArrayList<>();
            long nextId = 1L;
            for (SellerPayoutItem item : items) {
                item.setId(nextId++);
                saved.add(item);
            }
            return saved;
        });

        SellerDashboardResponse dashboard = service.getDashboard(seller);

        assertThat(dashboard.grossRevenue()).isEqualByComparingTo("200.00");
        assertThat(dashboard.netRevenue()).isEqualByComparingTo("76.00");
        assertThat(dashboard.openPayoutAmount()).isEqualByComparingTo("76.00");
        assertThat(dashboard.returnRequests()).isEqualTo(1);
        assertThat(dashboard.totalOrders()).isEqualTo(1);

        assertThat(storedPayouts).hasSize(1);
        SellerPayout payout = storedPayouts.getFirst();
        assertThat(payout.getGrossSalesAmount()).isEqualByComparingTo("200.00");
        assertThat(payout.getDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(payout.getCommissionAmount()).isEqualByComparingTo("18.00");
        assertThat(payout.getFeeAmount()).isEqualByComparingTo("10.00");
        assertThat(payout.getOpenReturnHoldbackAmount()).isEqualByComparingTo("76.00");
        assertThat(payout.getNetPayoutAmount()).isEqualByComparingTo("76.00");
        verify(sellerPayoutItemRepository).saveAll(any());
    }

    private User sellerUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("demo_seller_alpha");
        user.setEmail("seller.alpha@demo.de");
        user.setUserType(UserType.BUSINESS);
        user.getRoles().add(UserRole.SELLER);
        return user;
    }

    private SellerProfile sellerProfile(User seller) {
        SellerProfile profile = new SellerProfile();
        profile.setId(1L);
        profile.setUser(seller);
        profile.setDisplayName("TechPartner GmbH");
        profile.setCommissionRate(new BigDecimal("10.00"));
        profile.setPayoutIban("DE02120300000000202051");
        return profile;
    }

    private Order deliveredOrder() {
        User customer = new User();
        customer.setId(50L);
        customer.setUsername("kunde");
        customer.setEmail("kunde@example.com");

        Order order = new Order();
        order.setId(100L);
        order.setCustomer(customer);
        order.setCustomerName("Mia Kunde");
        order.setCustomerEmail("kunde@example.com");
        order.setOrderNumber("ORD-SELLER-001");
        order.setStatus(OrderStatus.DELIVERED);
        order.setCreatedAt(Instant.parse("2026-05-10T10:15:00Z"));
        order.setDeliveredAt(Instant.parse("2026-05-12T09:30:00Z"));
        order.setDiscountAmount(new BigDecimal("20.00"));
        order.setShippingCost(new BigDecimal("10.00"));
        order.setTotalPrice(new BigDecimal("228.00"));
        order.setTaxAmount(new BigDecimal("38.00"));
        return order;
    }

    private OrderItem orderItem(Order order, String sellerName) {
        Product product = new Product();
        product.setId(200L);
        product.setName("Business Dock");
        product.setSellerName(sellerName);

        OrderItem item = new OrderItem();
        item.setId(300L);
        item.setOrder(order);
        item.setProduct(product);
        item.setSellerName(sellerName);
        item.setQuantity(2);
        item.setPriceAtOrderTime(new BigDecimal("100.00"));
        return item;
    }
}
