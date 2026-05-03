package de.fhdw.webshop.returnrequest;

import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.returnrequest.dto.CreateReturnRequest;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestResponse;
import de.fhdw.webshop.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReturnRequestServiceTest {

    @Test
    void createsReturnRequestForSelectedItemsWithinReturnWindow() {
        ReturnRequestRepository returnRequestRepository = mock(ReturnRequestRepository.class);
        ReturnRequestItemRepository returnRequestItemRepository = mock(ReturnRequestItemRepository.class);
        ReturnRequestImageRepository returnRequestImageRepository = mock(ReturnRequestImageRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        ReturnRequestService service = new ReturnRequestService(
                returnRequestRepository,
                returnRequestItemRepository,
                returnRequestImageRepository,
                orderRepository);
        User customer = customer();
        Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
        OrderItem selectedItem = orderItem(101L, order, "Laptop Pro");
        OrderItem otherItem = orderItem(102L, order, "Office Chair");
        order.getItems().addAll(List.of(selectedItem, otherItem));

        when(orderRepository.findByIdAndCustomerId(order.getId(), customer.getId())).thenReturn(Optional.of(order));
        when(returnRequestItemRepository.existsByOrderItemId(selectedItem.getId())).thenReturn(false);
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(invocation -> {
            ReturnRequest request = invocation.getArgument(0);
            request.setId(501L);
            return request;
        });

        ReturnRequestResponse response = service.createReturnRequest(
                customer,
                new CreateReturnRequest(order.getId(), ReturnReason.DOES_NOT_FIT, List.of(selectedItem.getId()), null, List.of()));

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.reason()).isEqualTo(ReturnReason.DOES_NOT_FIT);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().orderItemId()).isEqualTo(selectedItem.getId());
        assertThat(response.shippingLabel().trackingId()).startsWith("WSR-");
        assertThat(response.shippingLabel().labelPdfUrl()).isEqualTo("/api/returns/501/label.pdf");
        assertThat(response.shippingLabel().returnCenterAddress().name()).contains("Ruecksendezentrum");
        assertThat(response.shippingLabel().senderAddress().name()).isEqualTo("alice");
        verify(returnRequestRepository).save(any(ReturnRequest.class));
    }

    @Test
    void rejectsReturnRequestsAfterFourteenDaysFromDelivery() {
        ReturnRequestRepository returnRequestRepository = mock(ReturnRequestRepository.class);
        ReturnRequestItemRepository returnRequestItemRepository = mock(ReturnRequestItemRepository.class);
        ReturnRequestImageRepository returnRequestImageRepository = mock(ReturnRequestImageRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        ReturnRequestService service = new ReturnRequestService(
                returnRequestRepository,
                returnRequestItemRepository,
                returnRequestImageRepository,
                orderRepository);
        User customer = customer();
        Order order = deliveredOrder(customer, Instant.now().minusSeconds(15 * 24 * 60 * 60));
        OrderItem item = orderItem(101L, order, "Laptop Pro");
        order.getItems().add(item);

        when(orderRepository.findByIdAndCustomerId(order.getId(), customer.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createReturnRequest(
                customer,
                new CreateReturnRequest(order.getId(), ReturnReason.DEFECTIVE, List.of(item.getId()), null, List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("14 Tagen");
        verify(returnRequestRepository, never()).save(any(ReturnRequest.class));
    }

    @Test
    void storesDefectDescriptionAndImagesForDefectiveReturns() {
        ReturnRequestRepository returnRequestRepository = mock(ReturnRequestRepository.class);
        ReturnRequestItemRepository returnRequestItemRepository = mock(ReturnRequestItemRepository.class);
        ReturnRequestImageRepository returnRequestImageRepository = mock(ReturnRequestImageRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        ReturnRequestService service = new ReturnRequestService(
                returnRequestRepository,
                returnRequestItemRepository,
                returnRequestImageRepository,
                orderRepository);
        User customer = customer();
        Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
        OrderItem item = orderItem(101L, order, "Laptop Pro");
        order.getItems().add(item);

        when(orderRepository.findByIdAndCustomerId(order.getId(), customer.getId())).thenReturn(Optional.of(order));
        when(returnRequestItemRepository.existsByOrderItemId(item.getId())).thenReturn(false);
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(invocation -> {
            ReturnRequest request = invocation.getArgument(0);
            request.setId(502L);
            request.getDefectImages().getFirst().setId(601L);
            return request;
        });

        ReturnRequestResponse response = service.createReturnRequest(
                customer,
                new CreateReturnRequest(
                        order.getId(),
                        ReturnReason.DEFECTIVE,
                        List.of(item.getId()),
                        "Display flackert nach wenigen Minuten.",
                        List.of(new de.fhdw.webshop.returnrequest.dto.ReturnRequestImageUpload(
                                "display.png",
                                "image/png",
                                4,
                                "iVBORw=="))));

        assertThat(response.reason()).isEqualTo(ReturnReason.DEFECTIVE);
        assertThat(response.defectDescription()).isEqualTo("Display flackert nach wenigen Minuten.");
        assertThat(response.defectImages()).hasSize(1);
        assertThat(response.defectImages().getFirst().fileName()).isEqualTo("display.png");
        assertThat(response.defectImages().getFirst().downloadUrl()).isEqualTo("/api/returns/502/images/601");
    }

    @Test
    void rejectsMoreThanThreeDefectImages() {
        ReturnRequestRepository returnRequestRepository = mock(ReturnRequestRepository.class);
        ReturnRequestItemRepository returnRequestItemRepository = mock(ReturnRequestItemRepository.class);
        ReturnRequestImageRepository returnRequestImageRepository = mock(ReturnRequestImageRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        ReturnRequestService service = new ReturnRequestService(
                returnRequestRepository,
                returnRequestItemRepository,
                returnRequestImageRepository,
                orderRepository);
        User customer = customer();
        Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));

        when(orderRepository.findByIdAndCustomerId(order.getId(), customer.getId())).thenReturn(Optional.of(order));

        var image = new de.fhdw.webshop.returnrequest.dto.ReturnRequestImageUpload("bild.jpg", "image/jpeg", 4, "/9j/");

        assertThatThrownBy(() -> service.createReturnRequest(
                customer,
                new CreateReturnRequest(
                        order.getId(),
                        ReturnReason.DEFECTIVE,
                        List.of(101L),
                        null,
                        List.of(image, image, image, image))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximal 3");
        verify(returnRequestRepository, never()).save(any(ReturnRequest.class));
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

    private static OrderItem orderItem(Long id, Order order, String productName) {
        Product product = new Product();
        product.setId(id + 1000);
        product.setName(productName);

        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setPriceAtOrderTime(new BigDecimal("99.99"));
        return item;
    }
}
