package de.fhdw.webshop.returnrequest;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.returnrequest.dto.CreateReturnRequestItem;
import de.fhdw.webshop.returnrequest.dto.CreateReturnRequest;
import de.fhdw.webshop.returnrequest.dto.ExternalRefundConfirmationRequest;
import de.fhdw.webshop.returnrequest.dto.InspectReturnRequest;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestImageDownload;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestImageResponse;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestImageUpload;
import de.fhdw.webshop.returnrequest.dto.ReturnLabelAddressResponse;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestItemResponse;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestResponse;
import de.fhdw.webshop.returnrequest.dto.ReturnShippingLabelResponse;
import de.fhdw.webshop.returnrequest.dto.ReturnStatusDecisionRequest;
import de.fhdw.webshop.user.User;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReturnRequestService {

    private static final Duration RETURN_WINDOW = Duration.ofDays(14);
    private static final String CARRIER_NAME = "Webshop Retouren";
    private static final String RETURN_CENTER_NAME = "Webshop Ruecksendezentrum";
    private static final String RETURN_CENTER_STREET = "Retourenstrasse 12";
    private static final String RETURN_CENTER_POSTAL_CODE = "33602";
    private static final String RETURN_CENTER_CITY = "Bielefeld";
    private static final String RETURN_CENTER_COUNTRY = "Deutschland";
    private static final int MAX_DEFECT_IMAGE_COUNT = 3;
    private static final long MAX_DEFECT_IMAGE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int QR_MATRIX_SIZE = 33;
    private static final Pattern RMA_CODE_PATTERN = Pattern.compile("^(?:RMA[-\\s#]*)?(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter LABEL_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.of("Europe/Berlin"));

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestItemRepository returnRequestItemRepository;
    private final ReturnRequestImageRepository returnRequestImageRepository;
    private final OrderRepository orderRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public ReturnRequestResponse createReturnRequest(User customer, CreateReturnRequest request) {
        Order order = orderRepository.findByIdAndCustomerId(request.orderId(), customer.getId())
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + request.orderId()));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Retouren koennen nur fuer zugestellte Bestellungen angemeldet werden.");
        }

        Instant deliveredAt = resolveDeliveredAt(order);
        if (Instant.now().isAfter(deliveredAt.plus(RETURN_WINDOW))) {
            throw new IllegalStateException("Die Rueckgabefrist von 14 Tagen nach Lieferdatum ist abgelaufen.");
        }

        List<RequestedReturnLine> requestedLines = normalizeRequestedLines(request, order);
        if (requestedLines.isEmpty()) {
            throw new IllegalArgumentException("Bitte waehle mindestens einen Artikel fuer die Retoure aus.");
        }

        Map<Long, OrderItem> orderItemsById = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        ReturnReason overallReason = resolveOverallReason(request, requestedLines);

        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setCustomer(customer);
        returnRequest.setOrder(order);
        returnRequest.setReason(overallReason);
        attachDefectDetails(returnRequest, request, requestedLines);
        attachShippingLabel(returnRequest, customer, order);

        for (RequestedReturnLine line : requestedLines) {
            OrderItem orderItem = orderItemsById.get(line.orderItemId());
            if (orderItem == null) {
                throw new IllegalArgumentException("Der Artikel gehoert nicht zu dieser Bestellung: " + line.orderItemId());
            }
            int alreadyReturned = getActiveReturnedQuantity(orderItem.getId());
            int returnableQuantity = orderItem.getQuantity() - alreadyReturned;
            if (line.quantity() > returnableQuantity) {
                throw new IllegalStateException("Fuer " + orderItem.getProduct().getName()
                        + " koennen maximal " + Math.max(returnableQuantity, 0)
                        + " Stueck retourniert werden.");
            }
            validateLineReason(line);

            ReturnRequestItem returnItem = new ReturnRequestItem();
            returnItem.setReturnRequest(returnRequest);
            returnItem.setOrderItem(orderItem);
            returnItem.setProductName(orderItem.getProduct().getName());
            returnItem.setQuantity(line.quantity());
            returnItem.setReason(line.reason());
            returnItem.setCustomerComment(line.comment());


            BigDecimal originalPrice = orderItem.getPriceAtOrderTime().multiply(BigDecimal.valueOf(line.quantity()));
            

            Order orderRetour = orderItem.getOrder();
            BigDecimal discountShare = BigDecimal.ZERO;
            

            if (orderRetour.getDiscountAmount() != null && orderRetour.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                
                BigDecimal orderTotalItemValue = order.getItems().stream()
                        .map(item -> item.getPriceAtOrderTime().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                if (orderTotalItemValue.compareTo(BigDecimal.ZERO) > 0) {

                    BigDecimal shareRatio = originalPrice.divide(orderTotalItemValue, 4, java.math.RoundingMode.HALF_UP);
                    discountShare = orderRetour.getDiscountAmount().multiply(shareRatio).setScale(2, java.math.RoundingMode.HALF_UP);
                }
            }
            BigDecimal finalRefund = originalPrice.subtract(discountShare).max(BigDecimal.ZERO);
            
            returnItem.setOriginalTotalPrice(originalPrice);
            returnItem.setDiscountShare(discountShare);
            returnItem.setRefundAmount(finalRefund);
            returnRequest.getItems().add(returnItem);
        }

        BigDecimal totalCouponDeduction = returnRequest.getItems().stream()
                .map(item -> item.getDiscountShare() != null ? item.getDiscountShare() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        returnRequest.setCouponDeduction(totalCouponDeduction);

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        auditLogService.record(customer, "RETURN_REQUEST_CREATED", "ReturnRequest", saved.getId(),
                AuditInitiator.USER, "Retoure " + saved.getId() + " fuer Bestellung "
                        + order.getOrderNumber() + " angemeldet.");
        return toResponse(saved);
    }

    private List<RequestedReturnLine> normalizeRequestedLines(CreateReturnRequest request, Order order) {
        if (!request.items().isEmpty()) {
            LinkedHashSet<Long> seenOrderItemIds = new LinkedHashSet<>();
            return request.items().stream()
                    .map(item -> {
                        if (!seenOrderItemIds.add(item.orderItemId())) {
                            throw new IllegalArgumentException(
                                    "Jeder Artikel darf pro Retoure nur einmal ausgewaehlt werden.");
                        }
                        return new RequestedReturnLine(
                                item.orderItemId(),
                                item.quantity(),
                                item.reason(),
                                normalizeDescription(item.comment()));
                    })
                    .toList();
        }

        ReturnReason fallbackReason = request.reason() == null ? ReturnReason.OTHER : request.reason();
        LinkedHashSet<Long> requestedItemIds = new LinkedHashSet<>(request.orderItemIds());
        Map<Long, OrderItem> orderItemsById = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        return requestedItemIds.stream()
                .map(orderItemId -> {
                    OrderItem orderItem = orderItemsById.get(orderItemId);
                    int quantity = orderItem == null ? 1 : orderItem.getQuantity();
                    return new RequestedReturnLine(orderItemId, quantity, fallbackReason, null);
                })
                .toList();
    }

    private int getActiveReturnedQuantity(Long orderItemId) {
        Integer quantity = returnRequestItemRepository.sumReturnedQuantityForOrderItem(
                orderItemId,
                List.of(ReturnRequestStatus.REJECTED));
        return quantity == null ? 0 : quantity;
    }

    private ReturnReason resolveOverallReason(CreateReturnRequest request, List<RequestedReturnLine> requestedLines) {
        if (request.reason() != null) {
            return request.reason();
        }
        if (requestedLines.isEmpty()) {
            return ReturnReason.OTHER;
        }
        ReturnReason firstReason = requestedLines.getFirst().reason();
        boolean allSameReason = requestedLines.stream().allMatch(line -> line.reason() == firstReason);
        return allSameReason ? firstReason : ReturnReason.OTHER;
    }

    private void validateLineReason(RequestedReturnLine line) {
        if (line.quantity() < 1) {
            throw new IllegalArgumentException("Die Retourenmenge muss mindestens 1 betragen.");
        }
        if (line.reason() == ReturnReason.OTHER && (line.comment() == null || line.comment().isBlank())) {
            throw new IllegalArgumentException("Bei 'Sonstiges' muss ein Kommentar angegeben werden.");
        }
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> listForCustomer(Long customerId) {
        return returnRequestRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> listAllForSupport() {
        return returnRequestRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> listOpenForWarehouse() {
        return returnRequestRepository.findByStatusOrderByCreatedAtDesc(ReturnRequestStatus.SUBMITTED).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReturnRequestResponse lookupForWarehouse(String code) {
        return toResponse(findByScanCode(code));
    }

    @Transactional
    public ReturnRequestResponse inspectReturnRequest(Long returnRequestId, InspectReturnRequest request) {
        ReturnRequest returnRequest = returnRequestRepository.findById(returnRequestId)
                .orElseThrow(() -> new EntityNotFoundException("Return request not found: " + returnRequestId));

        if (returnRequest.getStatus() != ReturnRequestStatus.GOODS_RECEIVED
                && returnRequest.getStatus() != ReturnRequestStatus.IN_REVIEW
                && returnRequest.getStatus() != ReturnRequestStatus.APPROVED) {
            throw new IllegalStateException("Ein Pruefvermerk ist erst nach dem Wareneingang moeglich.");
        }

        returnRequest.setInspectionCondition(request.condition());
        returnRequest.setInspectedAt(Instant.now());

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        auditLogService.recordSystemAction("RETURN_REQUEST_INSPECTED", "ReturnRequest", saved.getId(),
                "Pruefvermerk fuer Retoure aktualisiert: " + request.condition());
        return toResponse(saved);
    }

    @Transactional
    public ReturnRequestResponse markInReview(Long returnRequestId, User employee) {
        ReturnRequest returnRequest = loadReturnRequest(returnRequestId);
        if (returnRequest.getStatus() != ReturnRequestStatus.GOODS_RECEIVED) {
            throw new IllegalStateException(
                    "Eine Retoure kann erst nach dem Wareneingang in Pruefung gesetzt werden.");
        }
        returnRequest.setStatus(ReturnRequestStatus.IN_REVIEW);
        returnRequest.setDecidedBy(employee);
        return auditAndRespond(returnRequest, employee, "RETURN_REQUEST_IN_REVIEW", "Retoure wird geprueft.");
    }

    @Transactional
    public ReturnRequestResponse approveReturn(Long returnRequestId, User employee) {
        ReturnRequest returnRequest = loadReturnRequest(returnRequestId);
        if (returnRequest.getStatus() != ReturnRequestStatus.IN_REVIEW) {
            throw new IllegalStateException("Nur Retouren in Pruefung koennen freigegeben werden.");
        }
        returnRequest.setStatus(ReturnRequestStatus.APPROVED);
        returnRequest.setApprovedAt(Instant.now());
        returnRequest.setInspectedAt(Instant.now());
        returnRequest.setDecidedBy(employee);
        prepareRefund(returnRequest);
        return auditAndRespond(returnRequest, employee, "RETURN_REQUEST_APPROVED",
                "Retoure freigegeben. Rueckerstattung fuer Fremdsystem vorbereitet: "
                        + returnRequest.getRefundReference());
    }

    @Transactional
    public ReturnRequestResponse rejectReturn(Long returnRequestId, User employee, ReturnStatusDecisionRequest request) {
        ReturnRequest returnRequest = loadReturnRequest(returnRequestId);
        if (returnRequest.getStatus() != ReturnRequestStatus.IN_REVIEW) {
            throw new IllegalStateException("Nur Retouren in Pruefung koennen abgelehnt werden.");
        }
        String reason = normalizeRequiredDecisionReason(request.reason());
        returnRequest.setStatus(ReturnRequestStatus.REJECTED);
        returnRequest.setRejectedAt(Instant.now());
        returnRequest.setInspectedAt(Instant.now());
        returnRequest.setDecisionReason(reason);
        returnRequest.setDecidedBy(employee);
        rejectRefund(returnRequest);
        return auditAndRespond(returnRequest, employee, "RETURN_REQUEST_REJECTED", "Retoure abgelehnt: " + reason);
    }

    @Transactional
    public ReturnRequestResponse markGoodsReceived(Long returnRequestId, User employee) {
        ReturnRequest returnRequest = loadReturnRequest(returnRequestId);
        if (returnRequest.getStatus() != ReturnRequestStatus.SUBMITTED) {
            throw new IllegalStateException("Nur beantragte Retouren koennen als Wareneingang markiert werden.");
        }
        returnRequest.setStatus(ReturnRequestStatus.GOODS_RECEIVED);
        returnRequest.setGoodsReceivedAt(Instant.now());
        returnRequest.setDecidedBy(employee);
        restockReturnedItems(returnRequest);
        return auditAndRespond(returnRequest, employee, "RETURN_REQUEST_GOODS_RECEIVED",
                "Retourenware eingegangen und Bestand aktualisiert.");
    }

    @Transactional
    public ReturnRequestResponse confirmRefundFromExternalSystem(
            Long returnRequestId,
            ExternalRefundConfirmationRequest request) {
        ReturnRequest returnRequest = loadReturnRequest(returnRequestId);
        if (returnRequest.getStatus() != ReturnRequestStatus.APPROVED) {
            throw new IllegalStateException(
                    "Eine Rueckerstattung kann erst nach Freigabe durch den Mitarbeiter vom Fremdsystem bestaetigt werden.");
        }
        returnRequest.setStatus(ReturnRequestStatus.REFUNDED);
        returnRequest.setRefundedAt(Instant.now());
        returnRequest.setRefundReference(normalizeExternalRefundReference(request.reference()));

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        auditLogService.recordSystemAction("RETURN_REQUEST_REFUNDED", "ReturnRequest", saved.getId(),
                "Rueckerstattung durch Fremdsystem bestaetigt: " + saved.getRefundReference());
        return toResponse(saved);
    }

    private ReturnRequest loadReturnRequest(Long returnRequestId) {
        return returnRequestRepository.findById(returnRequestId)
                .orElseThrow(() -> new EntityNotFoundException("Return request not found: " + returnRequestId));
    }

    private ReturnRequestResponse auditAndRespond(
            ReturnRequest returnRequest,
            User employee,
            String action,
            String details) {
        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        auditLogService.record(employee, action, "ReturnRequest", saved.getId(), AuditInitiator.USER, details);
        return toResponse(saved);
    }

    private String normalizeRequiredDecisionReason(String reason) {
        String normalized = normalizeDescription(reason);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("Bitte gib einen Ablehnungsgrund an.");
        }
        return normalized;
    }

    private String normalizeExternalRefundReference(String reference) {
        String normalized = normalizeDescription(reference);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("Bitte gib eine Referenz des Fremdsystems an.");
        }
        if (normalized.length() > 80) {
            throw new IllegalArgumentException("Die Referenz des Fremdsystems darf maximal 80 Zeichen lang sein.");
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    public byte[] buildLabelPdf(Long customerId, Long returnRequestId) {
        ReturnRequest returnRequest = returnRequestRepository.findByIdAndCustomerId(returnRequestId, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Return request not found: " + returnRequestId));

        List<String> lines = new ArrayList<>();
        lines.add("Webshop Versandlabel");
        lines.add("Tracking-ID: " + returnRequest.getTrackingId());
        lines.add("Retoure: #" + returnRequest.getId() + " zu Bestellung " + returnRequest.getOrder().getOrderNumber());
        lines.add("Versanddienstleister: " + returnRequest.getCarrierName());
        lines.add("Erstellt: " + LABEL_DATE_FORMATTER.format(returnRequest.getLabelCreatedAt()));
        lines.add("");
        lines.add("Empfänger");
        lines.add(returnRequest.getReturnCenterName());
        lines.add(returnRequest.getReturnCenterStreet());
        lines.add(returnRequest.getReturnCenterPostalCode() + " " + returnRequest.getReturnCenterCity());
        lines.add(returnRequest.getReturnCenterCountry());
        lines.add("");
        lines.add("Absender");
        lines.add(returnRequest.getSenderName());
        lines.add(returnRequest.getSenderStreet());
        lines.add(returnRequest.getSenderPostalCode() + " " + returnRequest.getSenderCity());
        lines.add(returnRequest.getSenderCountry());
        lines.add("");
        lines.add("QR-Code Nutzdaten");
        lines.add(returnRequest.getQrCodePayload());
        lines.add("");
        lines.add("Artikel");
        returnRequest.getItems().forEach(item -> lines.add("- " + item.getQuantity() + " x " + item.getProductName()));

        return renderPdf(lines, returnRequest.getQrCodePayload());
    }

    @Transactional(readOnly = true)
    public ReturnRequestImageDownload loadImageForCustomer(Long customerId, Long returnRequestId, Long imageId) {
        ReturnRequestImage image = returnRequestImageRepository
                .findByIdAndReturnRequestIdAndReturnRequestCustomerId(imageId, returnRequestId, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Return request image not found: " + imageId));
        return toDownload(image);
    }

    @Transactional(readOnly = true)
    public ReturnRequestImageDownload loadImageForSupport(Long returnRequestId, Long imageId) {
        ReturnRequestImage image = returnRequestImageRepository.findByIdAndReturnRequestId(imageId, returnRequestId)
                .orElseThrow(() -> new EntityNotFoundException("Return request image not found: " + imageId));
        return toDownload(image);
    }

    private ReturnRequest findByScanCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isBlank()) {
            throw new IllegalArgumentException("Bitte Tracking-ID oder RMA-Nummer eingeben.");
        }

        return returnRequestRepository.findByTrackingIdIgnoreCase(code)
                .or(() -> parseRmaId(code).flatMap(returnRequestRepository::findById))
                .orElseThrow(() -> new EntityNotFoundException("Keine Retoure fuer den Scan-Code gefunden: " + code));
    }

    private java.util.Optional<Long> parseRmaId(String code) {
        Matcher matcher = RMA_CODE_PATTERN.matcher(code);
        if (!matcher.matches()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException ex) {
            return java.util.Optional.empty();
        }
    }

    private void restockReturnedItems(ReturnRequest returnRequest) {
        returnRequest.getItems().forEach(item -> {
            var product = item.getOrderItem().getProduct();
            product.setStock(product.getStock() + item.getQuantity());
        });
    }

    private void prepareRefund(ReturnRequest returnRequest) {
        returnRequest.setRefundStatus(ReturnRefundStatus.INITIATED);
        returnRequest.setRefundMethod(returnRequest.getOrder().getPaymentMethodType() == null
                ? ReturnRefundMethod.SHOP_CREDIT
                : ReturnRefundMethod.ORIGINAL_PAYMENT_METHOD);
        returnRequest.setRefundAmount(calculateRefundAmount(returnRequest));
        returnRequest.setRefundReference("RMA-" + returnRequest.getId() + "-REFUND");
    }

    private void rejectRefund(ReturnRequest returnRequest) {
        returnRequest.setRefundStatus(ReturnRefundStatus.REJECTED);
        returnRequest.setRefundMethod(null);
        returnRequest.setRefundAmount(BigDecimal.ZERO);
        returnRequest.setRefundReference("RMA-" + returnRequest.getId() + "-REJECTED");
    }

private BigDecimal calculateRefundAmount(ReturnRequest returnRequest) {
        return returnRequest.getItems().stream()
                .map(item -> item.getRefundAmount() != null ? item.getRefundAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateLineSubtotal(ReturnRequestItem item) {
        return calculateLineSubtotal(item.getOrderItem().getPriceAtOrderTime(), item.getQuantity());
    }

    private BigDecimal calculateLineSubtotal(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null || quantity <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    private void attachDefectDetails(
            ReturnRequest returnRequest,
            CreateReturnRequest request,
            List<RequestedReturnLine> requestedLines) {
        String description = normalizeDescription(request.defectDescription());
        List<ReturnRequestImageUpload> images = request.defectImages() == null ? List.of() : request.defectImages();
        boolean containsDefectiveLine = requestedLines.stream().anyMatch(line -> line.reason() == ReturnReason.DEFECTIVE);

        if (!containsDefectiveLine) {
            if (description != null || !images.isEmpty()) {
                throw new IllegalArgumentException(
                        "Defektdetails duerfen nur fuer den Rueckgabegrund Defekt uebermittelt werden.");
            }
            return;
        }

        returnRequest.setDefectDescription(description);
        if (images.size() > MAX_DEFECT_IMAGE_COUNT) {
            throw new IllegalArgumentException("Es duerfen maximal 3 Bilder hochgeladen werden.");
        }

        for (ReturnRequestImageUpload upload : images) {
            ReturnRequestImage image = toImageEntity(returnRequest, upload);
            returnRequest.getDefectImages().add(image);
        }
    }

    private ReturnRequestImage toImageEntity(ReturnRequest returnRequest, ReturnRequestImageUpload upload) {
        String contentType = upload.contentType() == null ? "" : upload.contentType().trim().toLowerCase(Locale.ROOT);
        String fileName = sanitizeFileName(upload.fileName());
        if (!isSupportedImageType(contentType, fileName)) {
            throw new IllegalArgumentException("Es sind nur JPG- oder PNG-Bilder erlaubt.");
        }

        byte[] imageData;
        try {
            imageData = Base64.getDecoder().decode(upload.dataBase64());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Bilddaten konnten nicht gelesen werden.", ex);
        }

        if (imageData.length == 0 || imageData.length > MAX_DEFECT_IMAGE_SIZE_BYTES
                || upload.sizeBytes() > MAX_DEFECT_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException("Jedes Bild darf maximal 5 MB gross sein.");
        }

        ReturnRequestImage image = new ReturnRequestImage();
        image.setReturnRequest(returnRequest);
        image.setFileName(fileName);
        image.setContentType(contentType);
        image.setSizeBytes(imageData.length);
        image.setImageData(imageData);
        return image;
    }

    private boolean isSupportedImageType(String contentType, String fileName) {
        boolean supportedType = "image/jpeg".equals(contentType) || "image/png".equals(contentType);
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        boolean supportedExtension = lowerFileName.endsWith(".jpg")
                || lowerFileName.endsWith(".jpeg")
                || lowerFileName.endsWith(".png");
        return supportedType && supportedExtension;
    }

    private String sanitizeFileName(String fileName) {
        String normalized = fileName == null ? "defekt-bild" : fileName.replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        String simpleName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        simpleName = simpleName.replace("\"", "").replace("\r", "").replace("\n", "").trim();
        return simpleName.isBlank() ? "defekt-bild" : simpleName;
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Die Fehlerbeschreibung darf maximal 500 Zeichen enthalten.");
        }
        return trimmed;
    }

    private Instant resolveDeliveredAt(Order order) {
        if (order.getDeliveredAt() != null) {
            return order.getDeliveredAt();
        }
        if (order.getCreatedAt() != null) {
            return order.getCreatedAt();
        }
        return Instant.EPOCH;
    }

    private void attachShippingLabel(ReturnRequest returnRequest, User customer, Order order) {
        String trackingId = buildTrackingId();
        returnRequest.setLabelCreatedAt(Instant.now());
        returnRequest.setCarrierName(CARRIER_NAME);
        returnRequest.setTrackingId(trackingId);
        returnRequest.setQrCodePayload("webshop-return:" + trackingId);
        returnRequest.setSenderName(
                firstNonBlank(order.getCustomerName(), customer.getUsername(), customer.getEmail(), "Kunde"));
        returnRequest.setSenderStreet(firstNonBlank(order.getDeliveryStreet(), "Adresse unbekannt"));
        returnRequest.setSenderPostalCode(firstNonBlank(order.getDeliveryPostalCode(), "00000"));
        returnRequest.setSenderCity(firstNonBlank(order.getDeliveryCity(), "Unbekannt"));
        returnRequest.setSenderCountry(firstNonBlank(order.getDeliveryCountry(), "Deutschland"));
        returnRequest.setReturnCenterName(RETURN_CENTER_NAME);
        returnRequest.setReturnCenterStreet(RETURN_CENTER_STREET);
        returnRequest.setReturnCenterPostalCode(RETURN_CENTER_POSTAL_CODE);
        returnRequest.setReturnCenterCity(RETURN_CENTER_CITY);
        returnRequest.setReturnCenterCountry(RETURN_CENTER_COUNTRY);
    }

    private String buildTrackingId() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
        return "WSR-" + suffix;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private ReturnRequestResponse toResponse(ReturnRequest returnRequest) {
        return new ReturnRequestResponse(
                returnRequest.getId(),
                returnRequest.getCustomer().getId(),
                returnRequest.getCustomer().getUsername(),
                returnRequest.getCustomer().getEmail(),
                returnRequest.getOrder().getId(),
                returnRequest.getOrder().getOrderNumber(),
                returnRequest.getReason(),
                returnRequest.getStatus(),
                returnRequest.getCreatedAt(),
                returnRequest.getInspectedAt(),
                returnRequest.getApprovedAt(),
                returnRequest.getRejectedAt(),
                returnRequest.getGoodsReceivedAt(),
                returnRequest.getRefundedAt(),
                returnRequest.getDecisionReason(),
                returnRequest.getDecidedBy() == null ? null : returnRequest.getDecidedBy().getId(),
                returnRequest.getDecidedBy() == null ? null : returnRequest.getDecidedBy().getUsername(),
                returnRequest.getInspectionCondition(),
                returnRequest.getRefundStatus(),
                returnRequest.getRefundMethod(),
                returnRequest.getRefundAmount(),
                returnRequest.getRefundReference(),
                returnRequest.getDefectDescription(),
                returnRequest.getDefectImages().stream()
                        .map(image -> toImageResponse(returnRequest, image))
                        .toList(),
                returnRequest.getItems().stream()
                        .map(item -> new ReturnRequestItemResponse(
                                item.getId(),
                                item.getOrderItem().getId(),
                                item.getProductName(),
                                item.getQuantity(),
                                item.getReason(),
                                item.getCustomerComment(),
                                calculateLineSubtotal(item),
                                item.getOriginalTotalPrice(),
                                item.getDiscountShare(),
                                item.getRefundAmount()
                            ))
                        .toList(),
                toShippingLabelResponse(returnRequest),
                returnRequest.getCouponDeduction());
    }

    private ReturnRequestImageResponse toImageResponse(ReturnRequest returnRequest, ReturnRequestImage image) {
        return new ReturnRequestImageResponse(
                image.getId(),
                image.getFileName(),
                image.getContentType(),
                image.getSizeBytes(),
                "/api/returns/" + returnRequest.getId() + "/images/" + image.getId());
    }

    private ReturnRequestImageDownload toDownload(ReturnRequestImage image) {
        return new ReturnRequestImageDownload(
                image.getFileName(),
                image.getContentType(),
                image.getImageData());
    }

    private ReturnShippingLabelResponse toShippingLabelResponse(ReturnRequest returnRequest) {
        return new ReturnShippingLabelResponse(
                returnRequest.getCarrierName(),
                returnRequest.getTrackingId(),
                "/api/returns/" + returnRequest.getId() + "/label.pdf",
                returnRequest.getQrCodePayload(),
                renderQrCodeSvg(returnRequest.getQrCodePayload()),
                returnRequest.getLabelCreatedAt(),
                new ReturnLabelAddressResponse(
                        returnRequest.getReturnCenterName(),
                        returnRequest.getReturnCenterStreet(),
                        returnRequest.getReturnCenterPostalCode(),
                        returnRequest.getReturnCenterCity(),
                        returnRequest.getReturnCenterCountry()),
                new ReturnLabelAddressResponse(
                        returnRequest.getSenderName(),
                        returnRequest.getSenderStreet(),
                        returnRequest.getSenderPostalCode(),
                        returnRequest.getSenderCity(),
                        returnRequest.getSenderCountry()));
    }

    private String renderQrCodeSvg(String payload) {
        BitMatrix matrix = createQrCodeMatrix(payload);
        int cellSize = 8;
        int size = matrix.getWidth() * cellSize;
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(size).append(' ').append(size)
                .append("\" role=\"img\" aria-label=\"QR-Code fuer Retourenlabel\">")
                .append("<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>");

        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    appendQrRect(svg, x, y, 1, 1, cellSize, "#111827");
                }
            }
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private void appendQrRect(StringBuilder svg, int x, int y, int width, int height, int cellSize, String fill) {
        svg.append("<rect x=\"").append(x * cellSize)
                .append("\" y=\"").append(y * cellSize)
                .append("\" width=\"").append(width * cellSize)
                .append("\" height=\"").append(height * cellSize)
                .append("\" fill=\"").append(fill).append("\"/>");
    }

    private BitMatrix createQrCodeMatrix(String payload) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                    EncodeHintType.MARGIN, 2);
            return new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, QR_MATRIX_SIZE, QR_MATRIX_SIZE, hints);
        } catch (WriterException ex) {
            throw new IllegalStateException("QR-Code konnte nicht erzeugt werden.", ex);
        }
    }

    private byte[] renderPdf(List<String> lines, String qrPayload) {
        StringBuilder content = new StringBuilder();
        content.append("BT\n/F1 16 Tf\n50 790 Td\n");
        for (int i = 0; i < lines.size(); i++) {
            if (i == 1) {
                content.append("/F1 12 Tf\n0 -26 Td\n");
            } else if (i > 1) {
                content.append("0 -18 Td\n");
            }
            content.append('(').append(escapePdfText(lines.get(i))).append(") Tj\n");
        }
        content.append("ET\n");
        appendQrCodePdf(content, qrPayload, 365, 545, 5);

        byte[] contentBytes = content.toString().getBytes(StandardCharsets.ISO_8859_1);
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + contentBytes.length + " >>\nstream\n" + content + "endstream");

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            pdf.append(i + 1).append(" 0 obj\n")
                    .append(objects.get(i)).append("\nendobj\n");
        }
        int xrefOffset = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
        pdf.append("0000000000 65535 f \n");
        for (Integer offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1)
                .append(" /Root 1 0 R >>\nstartxref\n")
                .append(xrefOffset)
                .append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private void appendQrCodePdf(StringBuilder content, String payload, int originX, int originY, int cellSize) {
        BitMatrix matrix = createQrCodeMatrix(payload);
        int totalCells = matrix.getWidth();
        int size = totalCells * cellSize;

        content.append("q\n")
                .append("1 1 1 rg\n")
                .append(originX).append(' ').append(originY).append(' ').append(size).append(' ').append(size)
                .append(" re f\n")
                .append("0 0 0 rg\n");

        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    appendQrRectPdf(content, originX, originY, x, y, 1, 1, totalCells, cellSize);
                }
            }
        }

        content.append("Q\n");
    }

    private void appendQrRectPdf(
            StringBuilder content,
            int originX,
            int originY,
            int x,
            int y,
            int width,
            int height,
            int totalCells,
            int cellSize) {
        int pdfX = originX + x * cellSize;
        int pdfY = originY + (totalCells - y - height) * cellSize;
        content.append(pdfX).append(' ')
                .append(pdfY).append(' ')
                .append(width * cellSize).append(' ')
                .append(height * cellSize).append(" re f\n");
    }

    private String escapePdfText(String rawText) {
        String asciiText = Normalizer.normalize(rawText == null ? "" : rawText, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("ß", "ss");
        return asciiText
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }
    private record RequestedReturnLine(
            Long orderItemId,
            int quantity,
            ReturnReason reason,
            String comment
    ) {}
}
