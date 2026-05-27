package de.fhdw.webshop.tradein;

import de.fhdw.webshop.discount.CouponRepository;
import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderItemRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.tradein.dto.CreateTradeInRequest;
import de.fhdw.webshop.tradein.dto.TradeInResponse;
import de.fhdw.webshop.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeInServiceTest {

    @Test
    void createsTradeInAfterReturnWindowExpired() {
        TradeInRepository tradeInRepository = mock(TradeInRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        TradeInService service = new TradeInService(
                tradeInRepository,
                orderItemRepository,
                mock(CouponRepository.class),
                mock(EmailService.class));
        User customer = customer();
        Order order = deliveredOrder(customer, Instant.now().minusSeconds(16 * 24 * 60 * 60));
        OrderItem item = orderItem(101L, order, true);

        when(orderItemRepository.findByIdAndOrderCustomerId(item.getId(), customer.getId()))
                .thenReturn(Optional.of(item));
        when(tradeInRepository.existsByOrderItemIdAndStatusNot(item.getId(), TradeInStatus.REJECTED))
                .thenReturn(false);
        when(tradeInRepository.save(any(TradeInRequest.class))).thenAnswer(invocation -> {
            TradeInRequest request = invocation.getArgument(0);
            request.setId(501L);
            return request;
        });

        TradeInResponse response = service.createTradeIn(
                customer,
                new CreateTradeInRequest(item.getId(), TradeInCondition.LIKE_NEW));

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.estimatedValue()).isEqualByComparingTo("30.00");
        assertThat(response.status()).isEqualTo(TradeInStatus.PENDING);
        verify(tradeInRepository).save(any(TradeInRequest.class));
    }

    @Test
    void rejectsTradeInWithinReturnWindow() {
        TradeInRepository tradeInRepository = mock(TradeInRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        TradeInService service = new TradeInService(
                tradeInRepository,
                orderItemRepository,
                mock(CouponRepository.class),
                mock(EmailService.class));
        User customer = customer();
        Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
        OrderItem item = orderItem(101L, order, true);

        when(orderItemRepository.findByIdAndOrderCustomerId(item.getId(), customer.getId()))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.createTradeIn(
                customer,
                new CreateTradeInRequest(item.getId(), TradeInCondition.LIGHT_WEAR)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Retourenfrist");
        verify(tradeInRepository, never()).save(any(TradeInRequest.class));
    }

    @Test
    void rejectsTradeInWhenProductDisabled() {
        TradeInRepository tradeInRepository = mock(TradeInRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        TradeInService service = new TradeInService(
                tradeInRepository,
                orderItemRepository,
                mock(CouponRepository.class),
                mock(EmailService.class));
        User customer = customer();
        Order order = deliveredOrder(customer, Instant.now().minusSeconds(16 * 24 * 60 * 60));
        OrderItem item = orderItem(101L, order, false);

        when(orderItemRepository.findByIdAndOrderCustomerId(item.getId(), customer.getId()))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.createTradeIn(
                customer,
                new CreateTradeInRequest(item.getId(), TradeInCondition.DEFECTIVE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deaktiviert");
        verify(tradeInRepository, never()).save(any(TradeInRequest.class));
    }

    private static User customer() {
        User customer = new User();
        customer.setId(7L);
        customer.setUsername("alice");
        customer.setEmail("alice@example.test");
        return customer;
    }

    private static Order deliveredOrder(User customer, Instant deliveredAt) {
        Order order = new Order();
        order.setId(42L);
        order.setOrderNumber("ORD-TEST-42");
        order.setCustomer(customer);
        order.setStatus(OrderStatus.DELIVERED);
        order.setCreatedAt(deliveredAt.minusSeconds(24 * 60 * 60));
        order.setDeliveredAt(deliveredAt);
        return order;
    }

    private static OrderItem orderItem(Long id, Order order, boolean tradeInEnabled) {
        Product product = new Product();
        product.setId(id + 1000);
        product.setName("Laptop Pro");
        product.setTradeInEnabled(tradeInEnabled);

        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setPriceAtOrderTime(new BigDecimal("100.00"));
        return item;
    }
}
