package de.fhdw.webshop.invoicearchive;

import de.fhdw.webshop.accountlink.AccountLinkRepository;
import de.fhdw.webshop.invoicearchive.dto.InvoiceArchiveExportResponse;
import de.fhdw.webshop.invoicearchive.dto.InvoiceArchiveOrderResponse;
import de.fhdw.webshop.invoicearchive.dto.InvoiceArchiveRequesterResponse;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserType;
import jakarta.persistence.EntityNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceArchiveService {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final Instant ARCHIVE_START = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant ARCHIVE_END = Instant.parse("9999-12-31T23:59:59Z");

    private final OrderRepository orderRepository;
    private final AccountLinkRepository accountLinkRepository;
    private final Map<String, InvoiceArchiveExportJob> exportJobs = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<InvoiceArchiveRequesterResponse> listRequesters(User currentUser) {
        requireBusinessCustomer(currentUser);
        return accessibleUsers(currentUser).stream()
                .map(user -> toRequesterResponse(user, currentUser.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceArchiveOrderResponse> listInvoices(User currentUser, Instant from, Instant to, Long requesterId) {
        requireBusinessCustomer(currentUser);
        validateDateRange(from, to);
        List<Long> accessibleIds = accessibleUserIds(currentUser);
        if (requesterId != null && !accessibleIds.contains(requesterId)) {
            throw new IllegalArgumentException("Für diesen Besteller liegt keine Berechtigung vor.");
        }
        List<Long> filteredUserIds = requesterId == null ? accessibleIds : List.of(requesterId);
        Instant effectiveFrom = from == null ? ARCHIVE_START : from;
        Instant effectiveTo = to == null ? ARCHIVE_END : to;
        return orderRepository.findInvoiceArchiveOrders(filteredUserIds, effectiveFrom, effectiveTo).stream()
                .map(this::toOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceArchiveExportResponse createExport(User currentUser, List<Long> orderIds) {
        requireBusinessCustomer(currentUser);
        List<Long> requestedOrderIds = normalizeOrderIds(orderIds);
        List<Order> orders = loadAccessibleOrders(currentUser, requestedOrderIds);
        if (orders.size() != requestedOrderIds.size()) {
            throw new IllegalArgumentException("Mindestens eine ausgewählte Rechnung wurde nicht gefunden oder ist nicht freigegeben.");
        }

        String exportId = UUID.randomUUID().toString();
        String fileName = "rechnungsarchiv-" + FILE_DATE.format(Instant.now()) + ".zip";
        InvoiceArchiveExportJob job = new InvoiceArchiveExportJob(exportId, fileName, orders.size());
        exportJobs.put(exportId, job);

        List<Order> exportOrders = List.copyOf(orders);
        CompletableFuture.runAsync(() -> {
            try {
                job.markReady(buildZip(exportOrders));
            } catch (Exception ex) {
                job.markFailed("ZIP-Datei konnte nicht erstellt werden: " + ex.getMessage());
            }
        });

        return job.toResponse();
    }

    public InvoiceArchiveExportResponse getExport(String exportId) {
        return loadJob(exportId).toResponse();
    }

    public byte[] getExportContent(String exportId) {
        InvoiceArchiveExportJob job = loadJob(exportId);
        if (!job.isReady()) {
            throw new IllegalStateException("Der Export ist noch nicht bereit.");
        }
        return job.zipContent();
    }

    public String getExportFileName(String exportId) {
        return loadJob(exportId).fileName();
    }

    private InvoiceArchiveExportJob loadJob(String exportId) {
        InvoiceArchiveExportJob job = exportJobs.get(exportId);
        if (job == null) {
            throw new EntityNotFoundException("Rechnungsexport nicht gefunden: " + exportId);
        }
        return job;
    }

    private List<Long> normalizeOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens eine Rechnung auswählen.");
        }
        return orderIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private List<Order> loadAccessibleOrders(User currentUser, Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens eine gültige Rechnung auswählen.");
        }
        Map<Long, Order> byId = new LinkedHashMap<>();
        orderRepository.findInvoiceArchiveOrdersByIds(accessibleUserIds(currentUser), orderIds)
                .forEach(order -> byId.put(order.getId(), order));
        return orderIds.stream()
                .map(byId::get)
                .filter(order -> order != null)
                .toList();
    }

    private void requireBusinessCustomer(User currentUser) {
        if (currentUser == null
                || currentUser.getUserType() != UserType.BUSINESS
                || !currentUser.hasRole(UserRole.CUSTOMER)) {
            throw new IllegalArgumentException("Das Rechnungsarchiv ist nur für B2B-Administratoren verfügbar.");
        }
    }

    private void validateDateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Das Startdatum darf nicht nach dem Enddatum liegen.");
        }
    }

    private List<User> accessibleUsers(User currentUser) {
        Map<Long, User> users = new LinkedHashMap<>();
        users.put(currentUser.getId(), currentUser);
        accountLinkRepository.findAllForUserId(currentUser.getId()).forEach(link -> {
            User linked = link.getUserA().getId().equals(currentUser.getId()) ? link.getUserB() : link.getUserA();
            if (linked != null && linked.isActive()) {
                users.put(linked.getId(), linked);
            }
        });
        return users.values().stream()
                .sorted(Comparator.comparing(user -> !user.getId().equals(currentUser.getId())))
                .toList();
    }

    private List<Long> accessibleUserIds(User currentUser) {
        return accessibleUsers(currentUser).stream()
                .map(User::getId)
                .toList();
    }

    private InvoiceArchiveRequesterResponse toRequesterResponse(User user, Long currentUserId) {
        return new InvoiceArchiveRequesterResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCustomerNumber(),
                user.getId().equals(currentUserId));
    }

    private InvoiceArchiveOrderResponse toOrderResponse(Order order) {
        User requester = order.getCustomer();
        int itemCount = order.getItems().stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
        return new InvoiceArchiveOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                requester != null ? requester.getId() : null,
                requester != null ? requester.getUsername() : order.getCustomerName(),
                requester != null ? requester.getEmail() : order.getCustomerEmail(),
                requester != null ? requester.getCustomerNumber() : null,
                order.getCreatedAt(),
                order.getStatus(),
                order.getTotalPrice(),
                order.getTaxAmount(),
                itemCount,
                buildInvoiceNumber(order));
    }

    private byte[] buildZip(List<Order> orders) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Order order : orders) {
                String entryName = sanitizeFileName("rechnung-" + order.getOrderNumber() + ".pdf");
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(buildInvoicePdf(order));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private byte[] buildInvoicePdf(Order order) {
        List<String> lines = new ArrayList<>();
        lines.add("Rechnung " + buildInvoiceNumber(order));
        lines.add("Bestellung: " + order.getOrderNumber());
        lines.add("Datum: " + DISPLAY_DATE.format(order.getCreatedAt()));
        lines.add("Besteller: " + firstNonBlank(order.getCustomerName(), order.getCustomerEmail(), "Unbekannt"));
        lines.add("E-Mail: " + firstNonBlank(order.getCustomerEmail(), "-"));
        lines.add("");
        lines.add("Positionen:");
        for (OrderItem item : order.getItems()) {
            BigDecimal lineTotal = item.getPriceAtOrderTime().multiply(BigDecimal.valueOf(item.getQuantity()));
            lines.add(item.getQuantity() + " x " + item.getProduct().getName()
                    + " | Einzelpreis " + item.getPriceAtOrderTime() + " EUR"
                    + " | Gesamt " + lineTotal + " EUR");
        }
        lines.add("");
        lines.add("Steuer: " + order.getTaxAmount() + " EUR");
        lines.add("Versand: " + order.getShippingCost() + " EUR");
        lines.add("Gesamtbetrag: " + order.getTotalPrice() + " EUR");

        StringBuilder content = new StringBuilder("BT\n/F1 11 Tf\n50 790 Td\n14 TL\n");
        for (String line : lines) {
            content.append("(").append(escapePdfText(line)).append(") Tj\nT*\n");
        }
        content.append("ET\n");
        byte[] streamBytes = content.toString().getBytes(StandardCharsets.ISO_8859_1);

        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Length " + streamBytes.length + " >>\nstream\n" + content + "endstream"
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
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
        pdf.append("startxref\n").append(xrefOffset).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private String buildInvoiceNumber(Order order) {
        return "RE-" + order.getOrderNumber();
    }

    private String sanitizeFileName(String fileName) {
        return Normalizer.normalize(fileName, Normalizer.Form.NFKD)
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-+", "-");
    }

    private String escapePdfText(String text) {
        return normalizePdfText(text)
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private String normalizePdfText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("€", "EUR")
                .replace("–", "-")
                .replace("—", "-")
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("„", "\"");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
