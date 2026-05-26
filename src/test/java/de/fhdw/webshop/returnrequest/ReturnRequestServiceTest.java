package de.fhdw.webshop.returnrequest;

import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.returnrequest.dto.CreateReturnRequestItem;
import de.fhdw.webshop.returnrequest.dto.CreateReturnRequest;
import de.fhdw.webshop.returnrequest.dto.ExternalRefundConfirmationRequest;
import de.fhdw.webshop.returnrequest.dto.InspectReturnRequest;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestResponse;
import de.fhdw.webshop.returnrequest.dto.ReturnStatusDecisionRequest;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.PaymentMethodType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
                                orderRepository,
                                mock(AuditLogService.class));
                User customer = customer();
                Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
                OrderItem selectedItem = orderItem(101L, order, "Laptop Pro");
                OrderItem otherItem = orderItem(102L, order, "Office Chair");
                order.getItems().addAll(List.of(selectedItem, otherItem));

                when(orderRepository.findByIdAndCustomerId(order.getId(), customer.getId()))
                                .thenReturn(Optional.of(order));
                when(returnRequestItemRepository.existsByOrderItemId(selectedItem.getId())).thenReturn(false);
                when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(invocation -> {
                        ReturnRequest request = invocation.getArgument(0);
                        request.setId(501L);
                        return request;
                });

                ReturnRequestResponse response = service.createReturnRequest(
                                customer,
                                new CreateReturnRequest(order.getId(), ReturnReason.DOES_NOT_FIT,
                                                List.of(selectedItem.getId()), List.of(), null, List.of()));

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
        void createsPartialReturnFromPerItemPayloadAndUsesRemainingQuantity() {
                ReturnRequestRepository returnRequestRepository = mock(ReturnRequestRepository.class);
                ReturnRequestItemRepository returnRequestItemRepository = mock(ReturnRequestItemRepository.class);
                ReturnRequestImageRepository returnRequestImageRepository = mock(ReturnRequestImageRepository.class);
                OrderRepository orderRepository = mock(OrderRepository.class);
                ReturnRequestService service = new ReturnRequestService(
                                returnRequestRepository,
                                returnRequestItemRepository,
                                returnRequestImageRepository,
                                orderRepository,
                                mock(AuditLogService.class));
                User customer = customer();
                Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
                OrderItem item = orderItem(101L, order, "Laptop Pro");
                item.setQuantity(3);
                order.getItems().add(item);

                when(orderRepository.findByIdAndCustomerId(order.getId(), customer.getId()))
                                .thenReturn(Optional.of(order));
                when(returnRequestItemRepository.sumReturnedQuantityForOrderItem(item.getId(), List.of(ReturnRequestStatus.REJECTED)))
                                .thenReturn(1);
                when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(invocation -> {
                        ReturnRequest request = invocation.getArgument(0);
                        request.setId(511L);
                        return request;
                });

                ReturnRequestResponse response = service.createReturnRequest(
                                customer,
                                new CreateReturnRequest(
                                                order.getId(),
                                                null,
                                                List.of(),
                                                List.of(new CreateReturnRequestItem(
                                                                item.getId(),
                                                                2,
                                                                ReturnReason.DAMAGED,
                                                                null)),
                                                null,
                                                List.of()));

                assertThat(response.id()).isEqualTo(511L);
                assertThat(response.reason()).isEqualTo(ReturnReason.DAMAGED);
                assertThat(response.items()).hasSize(1);
                assertThat(response.items().getFirst().quantity()).isEqualTo(2);
                assertThat(response.items().getFirst().reason()).isEqualTo(ReturnReason.DAMAGED);
        }

        @Test
        void rejectsReturnRequestWhenRequestedQuantityExceedsRemainingQuantity() {
                ReturnRequestRepository returnRequestRepository = mock(ReturnRequestRepository.class);
                ReturnRequestItemRepository returnRequestItemRepository = mock(ReturnRequestItemRepository.class);
                ReturnRequestImageRepository returnRequestImageRepository = mock(ReturnRequestImageRepository.class);
                OrderRepository orderRepository = mock(OrderRepository.class);
                ReturnRequestService service = new ReturnRequestService(
                                returnRequestRepository,
                                returnRequestItemRepository,
                                returnRequestImageRepository,
                                orderRepository,
                                mock(AuditLogService.class));
                User customer = customer();
                Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
                OrderItem item = orderItem(101L, order, "Laptop Pro");
                item.setQuantity(2);
                order.getItems().add(item);

                when(orderRepository.findByIdAndCustomerId(order.getId(), customer.getId()))
                                .thenReturn(Optional.of(order));
                when(returnRequestItemRepository.sumReturnedQuantityForOrderItem(item.getId(), List.of(ReturnRequestStatus.REJECTED)))
                                .thenReturn(1);

                assertThatThrownBy(() -> service.createReturnRequest(
                                customer,
                                new CreateReturnRequest(
                                                order.getId(),
                                                null,
                                                List.of(),
                                                List.of(new CreateReturnRequestItem(
                                                                item.getId(),
                                                                2,
                                                                ReturnReason.DAMAGED,
                                                                null)),
                                                null,
                                                List.of())))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("maximal 1");
                verify(returnRequestRepository, never()).save(any(ReturnRequest.class));
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
                                orderRepository,
                                mock(AuditLogService.class));
                User customer = customer();
                Order order = deliveredOrder(customer, Instant.now().minusSeconds(15 * 24 * 60 * 60));
                OrderItem item = orderItem(101L, order, "Laptop Pro");
                order.getItems().add(item);

                when(orderRepository.findByIdAndCustomerId(order.getId(), customer.getId()))
                                .thenReturn(Optional.of(order));

                assertThatThrownBy(() -> service.createReturnRequest(
                                customer,
                                new CreateReturnRequest(order.getId(), ReturnReason.DEFECTIVE, List.of(item.getId()),
                                                List.of(), null, List.of())))
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
                                orderRepository,
                                mock(AuditLogService.class));
                User customer = customer();
                Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
                OrderItem item = orderItem(101L, order, "Laptop Pro");
                order.getItems().add(item);

                when(orderRepository.findByIdAndCustomerId(order.getId(), customer.getId()))
                                .thenReturn(Optional.of(order));
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
                                                List.of(),
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
                                orderRepository,
                                mock(AuditLogService.class));
                User customer = customer();
                Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));

                when(orderRepository.findByIdAndCustomerId(order.getId(), customer.getId()))
                                .thenReturn(Optional.of(order));

                var image = new de.fhdw.webshop.returnrequest.dto.ReturnRequestImageUpload("bild.jpg", "image/jpeg", 4,
                                "/9j/");

                assertThatThrownBy(() -> service.createReturnRequest(
                                customer,
                                new CreateReturnRequest(
                                                order.getId(),
                                                ReturnReason.DEFECTIVE,
                                                List.of(101L),
                                                List.of(),
                                                null,
                                                List.of(image, image, image, image))))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("maximal 3");
                verify(returnRequestRepository, never()).save(any(ReturnRequest.class));
        }

        @Test
        void inspectionNoteUpdatesReturnWithoutCompletingIt() {
                ReturnRequestRepository returnRequestRepository = mock(ReturnRequestRepository.class);
                ReturnRequestItemRepository returnRequestItemRepository = mock(ReturnRequestItemRepository.class);
                ReturnRequestImageRepository returnRequestImageRepository = mock(ReturnRequestImageRepository.class);
                OrderRepository orderRepository = mock(OrderRepository.class);
                ReturnRequestService service = new ReturnRequestService(
                                returnRequestRepository,
                                returnRequestItemRepository,
                                returnRequestImageRepository,
                                orderRepository,
                                mock(AuditLogService.class));
                User customer = customer();
                Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
                order.setPaymentMethodType(PaymentMethodType.CREDIT_CARD);
                OrderItem item = orderItem(101L, order, "Laptop Pro");
                item.setQuantity(2);
                item.getProduct().setStock(3);
                order.getItems().add(item);
                ReturnRequest returnRequest = submittedReturnRequest(701L, customer, order, item);
                returnRequest.setStatus(ReturnRequestStatus.GOODS_RECEIVED);

                when(returnRequestRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
                when(returnRequestRepository.save(any(ReturnRequest.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ReturnRequestResponse response = service.inspectReturnRequest(
                                returnRequest.getId(),
                                new InspectReturnRequest(ReturnInspectionCondition.GOOD));

                assertThat(response.status()).isEqualTo(ReturnRequestStatus.GOODS_RECEIVED);
                assertThat(response.inspectionCondition()).isEqualTo(ReturnInspectionCondition.GOOD);
                assertThat(response.inspectedAt()).isNotNull();
                assertThat(response.refundStatus()).isEqualTo(ReturnRefundStatus.NOT_STARTED);
                assertThat(item.getProduct().getStock()).isEqualTo(3);
        }

        @Test
        void approvalPreparesRefundCalculationIncludingOrderLevelDiscountProRata() {
                ReturnRequestRepository returnRequestRepository = mock(ReturnRequestRepository.class);
                ReturnRequestItemRepository returnRequestItemRepository = mock(ReturnRequestItemRepository.class);
                ReturnRequestImageRepository returnRequestImageRepository = mock(ReturnRequestImageRepository.class);
                OrderRepository orderRepository = mock(OrderRepository.class);
                ReturnRequestService service = new ReturnRequestService(
                                returnRequestRepository,
                                returnRequestItemRepository,
                                returnRequestImageRepository,
                                orderRepository,
                                mock(AuditLogService.class));
                User customer = customer();
                Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
                order.setPaymentMethodType(PaymentMethodType.CREDIT_CARD);
                order.setDiscountAmount(new BigDecimal("30.00"));

                OrderItem returnedItem = orderItem(101L, order, "Laptop Pro");
                returnedItem.setQuantity(2);
                returnedItem.setPriceAtOrderTime(new BigDecimal("100.00"));
                returnedItem.getProduct().setStock(5);

                OrderItem secondItem = orderItem(102L, order, "Monitor");
                secondItem.setQuantity(1);
                secondItem.setPriceAtOrderTime(new BigDecimal("50.00"));

                order.getItems().addAll(List.of(returnedItem, secondItem));

                ReturnRequest returnRequest = submittedReturnRequest(711L, customer, order, returnedItem);
                returnRequest.setStatus(ReturnRequestStatus.IN_REVIEW);
                returnRequest.getItems().getFirst().setQuantity(2);
                when(returnRequestRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
                when(returnRequestRepository.save(any(ReturnRequest.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ReturnRequestResponse response = service.approveReturn(
                                returnRequest.getId(),
                                customer);

                assertThat(response.status()).isEqualTo(ReturnRequestStatus.APPROVED);
                assertThat(response.refundAmount()).isEqualByComparingTo("176.00");
                assertThat(response.refundStatus()).isEqualTo(ReturnRefundStatus.INITIATED);
                assertThat(response.refundMethod()).isEqualTo(ReturnRefundMethod.ORIGINAL_PAYMENT_METHOD);
        }

        @Test
        void markGoodsReceivedMustHappenBeforeReviewAndExternalRefundRequiresApproval() {
                ReturnRequestRepository returnRequestRepository = mock(ReturnRequestRepository.class);
                ReturnRequestItemRepository returnRequestItemRepository = mock(ReturnRequestItemRepository.class);
                ReturnRequestImageRepository returnRequestImageRepository = mock(ReturnRequestImageRepository.class);
                OrderRepository orderRepository = mock(OrderRepository.class);
                ReturnRequestService service = new ReturnRequestService(
                                returnRequestRepository,
                                returnRequestItemRepository,
                                returnRequestImageRepository,
                                orderRepository,
                                mock(AuditLogService.class));
                User employee = customer();
                User customer = customer();
                Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
                OrderItem item = orderItem(101L, order, "Laptop Pro");
                item.getProduct().setStock(3);
                order.getItems().add(item);
                ReturnRequest returnRequest = submittedReturnRequest(721L, customer, order, item);

                when(returnRequestRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
                when(returnRequestRepository.save(any(ReturnRequest.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ReturnRequestResponse goodsReceivedResponse = service.markGoodsReceived(returnRequest.getId(), employee);

                assertThat(goodsReceivedResponse.status()).isEqualTo(ReturnRequestStatus.GOODS_RECEIVED);
                assertThat(goodsReceivedResponse.goodsReceivedAt()).isNotNull();
                assertThat(goodsReceivedResponse.inspectedAt()).isNull();
                assertThat(item.getProduct().getStock()).isEqualTo(4);

                assertThatThrownBy(() -> service.confirmRefundFromExternalSystem(
                                returnRequest.getId(),
                                new ExternalRefundConfirmationRequest("psp-test-721")))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("Freigabe");
        }

        @Test
        void rejectReturnRequiresDecisionReason() {
                ReturnRequestRepository returnRequestRepository = mock(ReturnRequestRepository.class);
                ReturnRequestItemRepository returnRequestItemRepository = mock(ReturnRequestItemRepository.class);
                ReturnRequestImageRepository returnRequestImageRepository = mock(ReturnRequestImageRepository.class);
                OrderRepository orderRepository = mock(OrderRepository.class);
                ReturnRequestService service = new ReturnRequestService(
                                returnRequestRepository,
                                returnRequestItemRepository,
                                returnRequestImageRepository,
                                orderRepository,
                                mock(AuditLogService.class));
                User employee = customer();
                User customer = customer();
                Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
                OrderItem item = orderItem(101L, order, "Laptop Pro");
                ReturnRequest returnRequest = submittedReturnRequest(801L, customer, order, item);
                returnRequest.setStatus(ReturnRequestStatus.IN_REVIEW);

                when(returnRequestRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));

                assertThatThrownBy(() -> service.rejectReturn(
                                returnRequest.getId(),
                                employee,
                                new ReturnStatusDecisionRequest("   ")))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Ablehnungsgrund");
        }

        @Test
        void inspectionNoteDoesNotRejectReturnOrRestockAgain() {
                ReturnRequestRepository returnRequestRepository = mock(ReturnRequestRepository.class);
                ReturnRequestItemRepository returnRequestItemRepository = mock(ReturnRequestItemRepository.class);
                ReturnRequestImageRepository returnRequestImageRepository = mock(ReturnRequestImageRepository.class);
                OrderRepository orderRepository = mock(OrderRepository.class);
                ReturnRequestService service = new ReturnRequestService(
                                returnRequestRepository,
                                returnRequestItemRepository,
                                returnRequestImageRepository,
                                orderRepository,
                                mock(AuditLogService.class));
                User customer = customer();
                Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
                OrderItem item = orderItem(101L, order, "Laptop Pro");
                item.getProduct().setStock(3);
                order.getItems().add(item);
                ReturnRequest returnRequest = submittedReturnRequest(702L, customer, order, item);
                returnRequest.setStatus(ReturnRequestStatus.IN_REVIEW);

                when(returnRequestRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
                when(returnRequestRepository.save(any(ReturnRequest.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ReturnRequestResponse response = service.inspectReturnRequest(
                                returnRequest.getId(),
                                new InspectReturnRequest(ReturnInspectionCondition.DEFECTIVE));

                assertThat(response.status()).isEqualTo(ReturnRequestStatus.IN_REVIEW);
                assertThat(response.inspectionCondition()).isEqualTo(ReturnInspectionCondition.DEFECTIVE);
                assertThat(response.refundStatus()).isEqualTo(ReturnRefundStatus.NOT_STARTED);
                assertThat(response.refundAmount()).isEqualByComparingTo("0.00");
                assertThat(item.getProduct().getStock()).isEqualTo(3);
        }

        @Test
        void externalRefundConfirmationCompletesApprovedReturn() {
                ReturnRequestRepository returnRequestRepository = mock(ReturnRequestRepository.class);
                ReturnRequestItemRepository returnRequestItemRepository = mock(ReturnRequestItemRepository.class);
                ReturnRequestImageRepository returnRequestImageRepository = mock(ReturnRequestImageRepository.class);
                OrderRepository orderRepository = mock(OrderRepository.class);
                ReturnRequestService service = new ReturnRequestService(
                                returnRequestRepository,
                                returnRequestItemRepository,
                                returnRequestImageRepository,
                                orderRepository,
                                mock(AuditLogService.class));
                User customer = customer();
                Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
                order.setPaymentMethodType(PaymentMethodType.CREDIT_CARD);
                OrderItem item = orderItem(101L, order, "Laptop Pro");
                order.getItems().add(item);
                ReturnRequest returnRequest = submittedReturnRequest(731L, customer, order, item);
                returnRequest.setStatus(ReturnRequestStatus.APPROVED);
                returnRequest.setRefundStatus(ReturnRefundStatus.INITIATED);
                returnRequest.setRefundAmount(new BigDecimal("99.99"));
                returnRequest.setRefundMethod(ReturnRefundMethod.ORIGINAL_PAYMENT_METHOD);
                returnRequest.setRefundReference("RMA-731-REFUND");

                when(returnRequestRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
                when(returnRequestRepository.save(any(ReturnRequest.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ReturnRequestResponse response = service.confirmRefundFromExternalSystem(
                                returnRequest.getId(),
                                new ExternalRefundConfirmationRequest("PAY-731"));

                assertThat(response.status()).isEqualTo(ReturnRequestStatus.REFUNDED);
                assertThat(response.refundedAt()).isNotNull();
                assertThat(response.refundReference()).isEqualTo("PAY-731");
                assertThat(response.refundAmount()).isEqualByComparingTo("99.99");
        }

        @Test
        void labelPdfContainsQrCodeDrawingCommands() {
                ReturnRequestRepository returnRequestRepository = mock(ReturnRequestRepository.class);
                ReturnRequestItemRepository returnRequestItemRepository = mock(ReturnRequestItemRepository.class);
                ReturnRequestImageRepository returnRequestImageRepository = mock(ReturnRequestImageRepository.class);
                OrderRepository orderRepository = mock(OrderRepository.class);
                ReturnRequestService service = new ReturnRequestService(
                                returnRequestRepository,
                                returnRequestItemRepository,
                                returnRequestImageRepository,
                                orderRepository,
                                mock(AuditLogService.class));
                User customer = customer();
                Order order = deliveredOrder(customer, Instant.now().minusSeconds(2 * 24 * 60 * 60));
                OrderItem item = orderItem(101L, order, "Laptop Pro");
                ReturnRequest returnRequest = submittedReturnRequest(703L, customer, order, item);

                when(returnRequestRepository.findByIdAndCustomerId(returnRequest.getId(), customer.getId()))
                                .thenReturn(Optional.of(returnRequest));

                byte[] pdf = service.buildLabelPdf(customer.getId(), returnRequest.getId());
                String pdfContent = new String(pdf, StandardCharsets.ISO_8859_1);

                assertThat(pdfContent).startsWith("%PDF-1.4");
                assertThat(pdfContent).contains("(QR-Code Nutzdaten) Tj");
                assertThat(pdfContent).contains("(webshop-return:WSR-TEST-703) Tj");
                assertThat(pdfContent).contains("365 545 165 165 re f");
                assertThat(pdfContent).contains(" re f\nQ");
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

        private static ReturnRequest submittedReturnRequest(Long id, User customer, Order order, OrderItem orderItem) {
                ReturnRequest returnRequest = new ReturnRequest();
                returnRequest.setId(id);
                returnRequest.setCustomer(customer);
                returnRequest.setOrder(order);
                returnRequest.setReason(ReturnReason.DOES_NOT_FIT);
                returnRequest.setStatus(ReturnRequestStatus.SUBMITTED);
                returnRequest.setTrackingId("WSR-TEST-" + id);
                returnRequest.setCarrierName("Webshop Retouren");
                returnRequest.setQrCodePayload("webshop-return:WSR-TEST-" + id);
                returnRequest.setLabelCreatedAt(Instant.now());
                returnRequest.setSenderName("alice");
                returnRequest.setSenderStreet("Main Street 1");
                returnRequest.setSenderPostalCode("33602");
                returnRequest.setSenderCity("Bielefeld");
                returnRequest.setSenderCountry("Deutschland");
                returnRequest.setReturnCenterName("Webshop Rücksendezentrum");
                returnRequest.setReturnCenterStreet("Retourenstraße 12");
                returnRequest.setReturnCenterPostalCode("33602");
                returnRequest.setReturnCenterCity("Bielefeld");
                returnRequest.setReturnCenterCountry("Deutschland");

                ReturnRequestItem item = new ReturnRequestItem();
                item.setId(900L + orderItem.getId());
                item.setReturnRequest(returnRequest);
                item.setOrderItem(orderItem);
                item.setProductName(orderItem.getProduct().getName());
                item.setQuantity(orderItem.getQuantity());
                returnRequest.getItems().add(item);
                return returnRequest;
        }
}
