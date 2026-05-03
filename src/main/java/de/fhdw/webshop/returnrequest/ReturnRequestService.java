package de.fhdw.webshop.returnrequest;

import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.returnrequest.dto.CreateReturnRequest;
import de.fhdw.webshop.returnrequest.dto.InspectReturnRequest;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestImageDownload;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestImageResponse;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestImageUpload;
import de.fhdw.webshop.returnrequest.dto.ReturnLabelAddressResponse;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestItemResponse;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestResponse;
import de.fhdw.webshop.returnrequest.dto.ReturnShippingLabelResponse;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final Pattern RMA_CODE_PATTERN = Pattern.compile("^(?:RMA[-\\s#]*)?(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter LABEL_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.of("Europe/Berlin"));

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestItemRepository returnRequestItemRepository;
    private final ReturnRequestImageRepository returnRequestImageRepository;
    private final OrderRepository orderRepository;

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

        LinkedHashSet<Long> requestedItemIds = new LinkedHashSet<>(request.orderItemIds());
        if (requestedItemIds.isEmpty()) {
            throw new IllegalArgumentException("Bitte waehle mindestens einen Artikel fuer die Retoure aus.");
        }

        Map<Long, OrderItem> orderItemsById = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setCustomer(customer);
        returnRequest.setOrder(order);
        returnRequest.setReason(request.reason());
        attachDefectDetails(returnRequest, request);
        attachShippingLabel(returnRequest, customer, order);

        for (Long orderItemId : requestedItemIds) {
            OrderItem orderItem = orderItemsById.get(orderItemId);
            if (orderItem == null) {
                throw new IllegalArgumentException("Der Artikel gehoert nicht zu dieser Bestellung: " + orderItemId);
            }
            if (returnRequestItemRepository.existsByOrderItemId(orderItemId)) {
                throw new IllegalStateException("Fuer diesen Artikel wurde bereits eine Retoure angemeldet.");
            }

            ReturnRequestItem returnItem = new ReturnRequestItem();
            returnItem.setReturnRequest(returnRequest);
            returnItem.setOrderItem(orderItem);
            returnItem.setProductName(orderItem.getProduct().getName());
            returnItem.setQuantity(orderItem.getQuantity());
            returnRequest.getItems().add(returnItem);
        }

        return toResponse(returnRequestRepository.save(returnRequest));
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

        if (returnRequest.getStatus() != ReturnRequestStatus.SUBMITTED) {
            throw new IllegalStateException("Nur offene Retouren koennen bewertet werden.");
        }

        returnRequest.setInspectionCondition(request.condition());
        returnRequest.setInspectedAt(Instant.now());
        returnRequest.setStatus(ReturnRequestStatus.COMPLETED);

        if (request.condition() == ReturnInspectionCondition.GOOD) {
            restockReturnedItems(returnRequest);
            initiateRefund(returnRequest);
        } else {
            rejectRefund(returnRequest);
        }

        return toResponse(returnRequestRepository.save(returnRequest));
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
        lines.add("Empfaenger");
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
        returnRequest.getItems().forEach(item ->
                lines.add("- " + item.getQuantity() + " x " + item.getProductName()));

        return renderPdf(lines);
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

    private void initiateRefund(ReturnRequest returnRequest) {
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
                .map(item -> item.getOrderItem().getPriceAtOrderTime().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void attachDefectDetails(ReturnRequest returnRequest, CreateReturnRequest request) {
        String description = normalizeDescription(request.defectDescription());
        List<ReturnRequestImageUpload> images = request.defectImages() == null ? List.of() : request.defectImages();

        if (request.reason() != ReturnReason.DEFECTIVE) {
            if (description != null || !images.isEmpty()) {
                throw new IllegalArgumentException("Defektdetails duerfen nur fuer den Rueckgabegrund Defekt uebermittelt werden.");
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

        if (imageData.length == 0 || imageData.length > MAX_DEFECT_IMAGE_SIZE_BYTES || upload.sizeBytes() > MAX_DEFECT_IMAGE_SIZE_BYTES) {
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
        returnRequest.setSenderName(firstNonBlank(order.getCustomerName(), customer.getUsername(), customer.getEmail(), "Kunde"));
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
                                item.getQuantity()))
                        .toList(),
                toShippingLabelResponse(returnRequest)
        );
    }

    private ReturnRequestImageResponse toImageResponse(ReturnRequest returnRequest, ReturnRequestImage image) {
        return new ReturnRequestImageResponse(
                image.getId(),
                image.getFileName(),
                image.getContentType(),
                image.getSizeBytes(),
                "/api/returns/" + returnRequest.getId() + "/images/" + image.getId()
        );
    }

    private ReturnRequestImageDownload toDownload(ReturnRequestImage image) {
        return new ReturnRequestImageDownload(
                image.getFileName(),
                image.getContentType(),
                image.getImageData()
        );
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
                        returnRequest.getSenderCountry())
        );
    }

    private String renderQrCodeSvg(String payload) {
        byte[] digest = sha256(payload);
        int cells = 25;
        int cellSize = 8;
        int quietZone = 4;
        int size = (cells + quietZone * 2) * cellSize;
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(size).append(' ').append(size)
                .append("\" role=\"img\" aria-label=\"QR-Code fuer Retourenlabel\">")
                .append("<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>");

        drawFinder(svg, quietZone, quietZone, cellSize);
        drawFinder(svg, cells - 7 + quietZone, quietZone, cellSize);
        drawFinder(svg, quietZone, cells - 7 + quietZone, cellSize);

        for (int y = 0; y < cells; y++) {
            for (int x = 0; x < cells; x++) {
                if (isFinderArea(x, y, cells)) {
                    continue;
                }
                int digestIndex = Math.floorMod((y * cells + x) * 7 + x + y, digest.length);
                int bit = (digest[digestIndex] >> ((x + y) % 8)) & 1;
                if (bit == 1) {
                    appendQrCell(svg, x + quietZone, y + quietZone, cellSize);
                }
            }
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private boolean isFinderArea(int x, int y, int cells) {
        return x < 8 && y < 8
                || x >= cells - 8 && y < 8
                || x < 8 && y >= cells - 8;
    }

    private void drawFinder(StringBuilder svg, int startX, int startY, int cellSize) {
        appendQrRect(svg, startX, startY, 7, 7, cellSize, "#111827");
        appendQrRect(svg, startX + 1, startY + 1, 5, 5, cellSize, "#ffffff");
        appendQrRect(svg, startX + 2, startY + 2, 3, 3, cellSize, "#111827");
    }

    private void appendQrCell(StringBuilder svg, int x, int y, int cellSize) {
        appendQrRect(svg, x, y, 1, 1, cellSize, "#111827");
    }

    private void appendQrRect(StringBuilder svg, int x, int y, int width, int height, int cellSize, String fill) {
        svg.append("<rect x=\"").append(x * cellSize)
                .append("\" y=\"").append(y * cellSize)
                .append("\" width=\"").append(width * cellSize)
                .append("\" height=\"").append(height * cellSize)
                .append("\" fill=\"").append(fill).append("\"/>");
    }

    private byte[] sha256(String payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private byte[] renderPdf(List<String> lines) {
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

        byte[] contentBytes = content.toString().getBytes(StandardCharsets.ISO_8859_1);
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + contentBytes.length + " >>\nstream\n" + content + "endstream"
        );

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

    private String escapePdfText(String rawText) {
        String asciiText = Normalizer.normalize(rawText == null ? "" : rawText, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("ß", "ss");
        return asciiText
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }
}
