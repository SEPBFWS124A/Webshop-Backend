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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

    private static final String SHOP_NAME = "FHDW Webshop Projekt";
    private static final String LEGAL_ENTITY = "Fachhochschule der Wirtschaft (FHDW) gGmbH";
    private static final String COMPANY_STREET = "Marconistraße 10";
    private static final String COMPANY_POSTAL_CITY = "50769 Köln";
    private static final String COMPANY_COUNTRY = "Deutschland";
    private static final String COMPANY_PHONE = "+49 (0)2202 9527-0";
    private static final String COMPANY_EMAIL = "info@fhdw.de";
    private static final String COMPANY_VAT_ID = "DE813485271";
    private static final String COMPANY_REGISTER = "Amtsgericht Paderborn · HRB 6857";
    private static final String COMPANY_REPRESENTATIVE = "Thomas Ströder";

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DISPLAY_DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy")
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
        BigDecimal subtotal = order.getItems().stream()
                .map(item -> amount(item.getPriceAtOrderTime()).multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = amount(order.getDiscountAmount());
        BigDecimal shipping = amount(order.getShippingCost());
        BigDecimal vat = amount(order.getTaxAmount());
        BigDecimal total = amount(order.getTotalPrice());
        BigDecimal netAmount = subtotal.subtract(discount).add(shipping).setScale(2, RoundingMode.HALF_UP);

        StringBuilder content = new StringBuilder();
        drawRect(content, 0, 784, 595, 58, "1e3a5f");
        drawText(content, "F2", 24, 55, 812, SHOP_NAME, "ffffff");
        drawText(content, "F1", 9, 55, 797,
                COMPANY_STREET + " · " + COMPANY_POSTAL_CITY + " · " + COMPANY_COUNTRY, "dbeafe");
        drawText(content, "F2", 22, 407, 811, "RECHNUNG", "ffffff");

        drawText(content, "F1", 7, 55, 760,
                SHOP_NAME + " · " + COMPANY_STREET + " · " + COMPANY_POSTAL_CITY, "6b7280");
        drawText(content, "F2", 11, 55, 739, firstNonBlank(order.getCustomerName(), "Kunde"), "111827");
        drawText(content, "F1", 9, 55, 724, firstNonBlank(order.getCustomerEmail(), "-"), "111827");
        drawText(content, "F1", 9, 55, 710, firstNonBlank(order.getDeliveryStreet(), "Adresse nicht hinterlegt"), "111827");
        drawText(content, "F1", 9, 55, 696,
                firstNonBlank(joinAddress(order.getDeliveryPostalCode(), order.getDeliveryCity()), "Ort nicht hinterlegt"), "111827");
        drawText(content, "F1", 9, 55, 682, firstNonBlank(order.getDeliveryCountry(), COMPANY_COUNTRY), "111827");

        drawRect(content, 344, 622, 196, 147, "f3f6fb");
        drawText(content, "F2", 8, 360, 748, "Rechnungsnummer", "64748b");
        drawText(content, "F1", 7, 360, 737, buildInvoiceNumber(order), "111827");
        drawText(content, "F2", 8, 360, 718, "Bestellnummer", "64748b");
        drawText(content, "F1", 7, 360, 707, order.getOrderNumber(), "111827");
        drawText(content, "F2", 8, 360, 688, "Rechnungsdatum", "64748b");
        drawText(content, "F1", 8, 360, 677, DISPLAY_DAY.format(Instant.now()), "111827");
        drawText(content, "F2", 8, 360, 658, "Leistungsdatum", "64748b");
        drawText(content, "F1", 8, 360, 647, DISPLAY_DAY.format(order.getCreatedAt()), "111827");
        drawText(content, "F2", 8, 360, 628, "Zahlungsart", "64748b");
        drawText(content, "F1", 8, 430, 628, formatPaymentMethod(order), "111827");

        drawText(content, "F2", 10, 55, 636, "Leistender Unternehmer", "1e3a5f");
        drawText(content, "F1", 8, 55, 621, LEGAL_ENTITY + " · " + SHOP_NAME, "374151");
        drawText(content, "F1", 8, 55, 608, COMPANY_STREET + " · " + COMPANY_POSTAL_CITY + " · " + COMPANY_COUNTRY, "374151");
        drawText(content, "F1", 8, 55, 595, "USt-IdNr.: " + COMPANY_VAT_ID + " · " + COMPANY_REGISTER, "374151");
        drawText(content, "F1", 8, 55, 582,
                "Vertreten durch: " + COMPANY_REPRESENTATIVE + " · " + COMPANY_EMAIL + " · " + COMPANY_PHONE, "374151");

        drawRect(content, 55, 535, 485, 28, "e8f0fe");
        drawText(content, "F2", 8, 64, 546, "Pos.", "1f2937");
        drawText(content, "F2", 8, 94, 546, "Artikel / handelsübliche Bezeichnung", "1f2937");
        drawText(content, "F2", 8, 304, 546, "Menge", "1f2937");
        drawText(content, "F2", 8, 350, 546, "Einzelpreis", "1f2937");
        drawText(content, "F2", 8, 428, 546, "USt.", "1f2937");
        drawText(content, "F2", 8, 485, 546, "Netto", "1f2937");

        int y = 511;
        int position = 1;
        for (OrderItem item : order.getItems()) {
            if (y < 372) {
                drawText(content, "F1", 8, 64, y, "Weitere Positionen siehe Bestelldetails im Archiv.", "6b7280");
                break;
            }
            BigDecimal lineTotal = amount(item.getPriceAtOrderTime())
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            drawText(content, "F1", 8, 64, y, String.valueOf(position), "111827");
            drawText(content, "F1", 8, 93, y, truncate(item.getProduct().getName(), 38), "111827");
            drawText(content, "F1", 8, 306, y, String.valueOf(item.getQuantity()), "111827");
            drawText(content, "F1", 8, 350, y, formatMoney(amount(item.getPriceAtOrderTime())), "111827");
            drawText(content, "F1", 8, 428, y, "19 %", "111827");
            drawText(content, "F1", 8, 485, y, formatMoney(lineTotal), "111827");
            drawLine(content, 55, y - 10, 540, y - 10, "e5e7eb");
            y -= 22;
            position += 1;
        }

        int totalsY = 318;
        drawRect(content, 318, totalsY - 111, 222, 134, "f8fafc");
        drawText(content, "F1", 8, 338, totalsY, "Zwischensumme netto", "374151");
        drawText(content, "F1", 8, 454, totalsY, formatMoney(subtotal), "111827");
        drawText(content, "F1", 8, 338, totalsY - 17, "Rabatt / Minderung", "374151");
        drawText(content, "F1", 8, 454, totalsY - 17, "-" + formatMoney(discount), "111827");
        drawText(content, "F1", 8, 338, totalsY - 34, "Versand netto", "374151");
        drawText(content, "F1", 8, 454, totalsY - 34, formatMoney(shipping), "111827");
        drawText(content, "F1", 8, 338, totalsY - 51, "Steuerbasis netto", "374151");
        drawText(content, "F1", 8, 454, totalsY - 51, formatMoney(netAmount), "111827");
        drawText(content, "F1", 8, 338, totalsY - 68, "Umsatzsteuer 19 %", "374151");
        drawText(content, "F1", 8, 454, totalsY - 68, formatMoney(vat), "111827");
        drawLine(content, 336, totalsY - 82, 520, totalsY - 82, "cbd5e1");
        drawText(content, "F2", 9, 338, totalsY - 102, "Rechnungsbetrag brutto", "111827");
        drawText(content, "F2", 9, 454, totalsY - 102, formatMoney(total), "111827");

        drawText(content, "F2", 9, 55, 174, "Hinweise", "1e3a5f");
        drawText(content, "F1", 7, 55, 159,
                "Der Leistungszeitpunkt entspricht dem Bestelldatum, sofern kein abweichendes Lieferdatum ausgewiesen ist.", "374151");
        drawText(content, "F1", 7, 55, 147,
                "Bitte bewahren Sie diese Rechnung für Ihre Buchhaltung auf. Beträge sind in Euro ausgewiesen.", "374151");
        drawText(content, "F1", 7, 55, 135,
                "Vielen Dank für Ihren Einkauf im Webshop.", "374151");

        drawLine(content, 55, 80, 540, 80, "cbd5e1");
        drawText(content, "F1", 7, 55, 62,
                SHOP_NAME + " · " + COMPANY_STREET + " · " + COMPANY_POSTAL_CITY + " · "
                        + COMPANY_EMAIL + " · " + COMPANY_PHONE, "6b7280");

        byte[] streamBytes = content.toString().getBytes(StandardCharsets.ISO_8859_1);

        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>",
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

    private BigDecimal amount(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMoney(BigDecimal value) {
        return amount(value).toPlainString() + " EUR";
    }

    private String formatPaymentMethod(Order order) {
        if (order.getPaymentMethodType() == null) {
            return "nicht hinterlegt";
        }
        return switch (order.getPaymentMethodType()) {
            case CREDIT_CARD -> "Kreditkarte";
            case BANK_TRANSFER -> "Überweisung";
            case SEPA_DIRECT_DEBIT -> "SEPA-Lastschrift";
        };
    }

    private String joinAddress(String postalCode, String city) {
        String normalizedPostalCode = postalCode == null ? "" : postalCode.trim();
        String normalizedCity = city == null ? "" : city.trim();
        return (normalizedPostalCode + " " + normalizedCity).trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + ".";
    }

    private void drawText(StringBuilder content, String font, int size, int x, int y, String text, String color) {
        content.append(hexColor(color)).append(" rg\n")
                .append("BT\n/")
                .append(font)
                .append(" ")
                .append(size)
                .append(" Tf\n")
                .append(x)
                .append(" ")
                .append(y)
                .append(" Td\n(")
                .append(escapePdfText(text))
                .append(") Tj\nET\n");
    }

    private void drawRect(StringBuilder content, int x, int y, int width, int height, String color) {
        content.append(hexColor(color)).append(" rg\n")
                .append(x).append(" ").append(y).append(" ")
                .append(width).append(" ").append(height).append(" re f\n");
    }

    private void drawLine(StringBuilder content, int x1, int y1, int x2, int y2, String color) {
        content.append(hexColor(color)).append(" RG\n")
                .append("0.8 w\n")
                .append(x1).append(" ").append(y1).append(" m\n")
                .append(x2).append(" ").append(y2).append(" l S\n");
    }

    private String hexColor(String hex) {
        int red = Integer.parseInt(hex.substring(0, 2), 16);
        int green = Integer.parseInt(hex.substring(2, 4), 16);
        int blue = Integer.parseInt(hex.substring(4, 6), 16);
        return formatColor(red) + " " + formatColor(green) + " " + formatColor(blue);
    }

    private String formatColor(int value) {
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(255), 3, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
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
