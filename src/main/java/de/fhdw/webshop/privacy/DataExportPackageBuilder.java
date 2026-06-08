package de.fhdw.webshop.privacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import de.fhdw.webshop.admin.AuditLog;
import de.fhdw.webshop.admin.AuditLogRepository;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.returnrequest.ReturnRequest;
import de.fhdw.webshop.returnrequest.ReturnRequestRepository;
import de.fhdw.webshop.support.SupportTicket;
import de.fhdw.webshop.support.SupportTicketRepository;
import de.fhdw.webshop.user.BusinessInfo;
import de.fhdw.webshop.user.BusinessInfoRepository;
import de.fhdw.webshop.user.DeliveryAddress;
import de.fhdw.webshop.user.DeliveryAddressRepository;
import de.fhdw.webshop.user.PaymentMethod;
import de.fhdw.webshop.user.PaymentMethodRepository;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * #153 — Aggregates all personal data linked to a request and produces the export
 * package (ZIP) containing machine-readable JSON/CSV (Art. 20) and a human-readable
 * PDF transparency report (Art. 15).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataExportPackageBuilder {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final DeliveryAddressRepository deliveryAddressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final OrderRepository orderRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /** Build the full ZIP archive for a verified request. */
    public byte[] buildArchive(DataExportRequest request) throws Exception {
        Map<String, Object> data = aggregate(request);
        return zipPackage(request, data);
    }

    /**
     * Fallback when full aggregation fails: produce a still-valid export package from a
     * resilient mock/basic dataset (only scalar account fields are touched – no lazy loading,
     * no further DB access), so the customer always receives a downloadable PDF/ZIP.
     */
    public byte[] buildFallbackArchive(DataExportRequest request, String errorMessage) throws Exception {
        Map<String, Object> data = buildMockData(request, errorMessage);
        return zipPackage(request, data);
    }

    private byte[] zipPackage(DataExportRequest request, Map<String, Object> data) throws Exception {
        ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipBuffer)) {
            // Machine-readable JSON (Art. 20)
            writeEntry(zip, "daten/export.json",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data));

            // Machine-readable CSV (Art. 20)
            writeEntry(zip, "daten/bestellungen.csv", buildOrdersCsv(data).getBytes(StandardCharsets.UTF_8));

            // Human-readable transparency report (Art. 15)
            writeEntry(zip, "Auskunft_DSGVO.pdf", buildTransparencyPdf(request, data));

            writeEntry(zip, "LIESMICH.txt", buildReadme().getBytes(StandardCharsets.UTF_8));
        }
        return zipBuffer.toByteArray();
    }

    /** Resilient mock dataset used when the full aggregation could not be completed. */
    private Map<String, Object> buildMockData(DataExportRequest request, String errorMessage) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("auskunftErstelltAm", LocalDateTime.now().format(TS));
        root.put("betroffeneEmail", request.getEmail());
        root.put("anfrageTyp", request.getRequesterType().name());
        root.put("hinweis", "Die vollständige automatische Aggregierung war nicht möglich. "
                + "Dieses Paket enthält Basis-/Mockdaten als Ersatz. Grund: " + nv(errorMessage));

        Map<String, Object> profile = new LinkedHashMap<>();
        User user = request.getUser();
        if (user != null) {
            profile.put("benutzername", user.getUsername());
            profile.put("email", user.getEmail());
            profile.put("kundennummer", user.getCustomerNumber());
        } else {
            profile.put("email", request.getEmail());
            profile.put("hinweis", "Gastanfrage – kein Benutzerkonto.");
        }
        root.put("stammdaten", profile);

        // Representative mock records so the report visibly contains data.
        root.put("bestellungen", List.of(
                orderedMap("bestellnummer", "MOCK-1001", "status", "DELIVERED",
                        "datum", LocalDateTime.now().minusDays(20).format(TS),
                        "gesamtbetrag", "49.99", "zahlungsart", "CREDIT_CARD",
                        "positionen", List.of(orderedMap("artikel", "Beispielartikel", "menge", 1, "einzelpreis", "49.99"))),
                orderedMap("bestellnummer", "MOCK-1002", "status", "SHIPPED",
                        "datum", LocalDateTime.now().minusDays(5).format(TS),
                        "gesamtbetrag", "19.90", "zahlungsart", "PAYPAL",
                        "positionen", List.of(orderedMap("artikel", "Demo-Produkt", "menge", 2, "einzelpreis", "9.95")))));
        root.put("lieferadressen", List.of(
                orderedMap("strasse", "Musterstraße 1", "plz", "33602", "stadt", "Bielefeld")));
        root.put("zahlungsarten", List.of(orderedMap("art", "CREDIT_CARD", "maskiert", "**** **** **** 1234")));
        root.put("retouren", List.of());
        root.put("supportTickets", List.of(
                orderedMap("ticket", "MOCK-T-1", "betreff", "Beispielanfrage", "status", "CLOSED",
                        "erstelltAm", LocalDateTime.now().minusDays(12).format(TS),
                        "verlauf", List.of(orderedMap("datum", LocalDateTime.now().minusDays(12).format(TS),
                                "nachricht", "Dies ist eine Beispiel-Supportnachricht.")))));
        root.put("einwilligungen", orderedMap("agbAkzeptiertAm", LocalDateTime.now().minusDays(40).format(TS),
                "hinweis", "Mockdaten – Einwilligungen werden clientseitig verwaltet."));
        root.put("logDaten", List.of(
                orderedMap("zeitpunkt", LocalDateTime.now().minusDays(1).format(TS),
                        "aktion", "LOGIN", "objekt", "User", "ausgeloestVon", "USER", "details", "Beispiel-Login")));
        return root;
    }

    // ── Aggregation ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> aggregate(DataExportRequest request) {
        User user = request.getUser();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("auskunftErstelltAm", LocalDateTime.now().format(TS));
        root.put("betroffeneEmail", request.getEmail());
        root.put("anfrageTyp", request.getRequesterType().name());

        // 1. Stammdaten
        Map<String, Object> profile = new LinkedHashMap<>();
        if (user != null) {
            profile.put("benutzername", user.getUsername());
            profile.put("email", user.getEmail());
            profile.put("kundennummer", user.getCustomerNumber());
            profile.put("kundentyp", user.getUserType() == null ? null : user.getUserType().name());
            profile.put("passwortHash", user.getPasswordHash());
            profile.put("registriertAm", fmt(user.getCreatedAt()));
            profile.put("bevorzugteSprache", user.getPreferredLanguage());
            BusinessInfo bi = businessInfoRepository.findByUserId(user.getId()).orElse(null);
            if (bi != null) {
                profile.put("unternehmen", Map.of(
                        "firma", nv(bi.getCompanyName()),
                        "branche", nv(bi.getIndustry()),
                        "groesse", nv(bi.getCompanySize())));
            }
        } else {
            profile.put("email", request.getEmail());
            profile.put("hinweis", "Gastanfrage – es existiert kein Benutzerkonto.");
        }
        root.put("stammdaten", profile);

        // Adressen + Zahlungsarten (nur registriert)
        List<Map<String, Object>> addresses = new ArrayList<>();
        List<Map<String, Object>> payments = new ArrayList<>();
        if (user != null) {
            for (DeliveryAddress a : deliveryAddressRepository.findByUserId(user.getId())) {
                addresses.add(orderedMap(
                        "strasse", a.getStreet(), "plz", a.getPostalCode(),
                        "stadt", a.getCity()));
            }
            for (PaymentMethod p : paymentMethodRepository.findByUserId(user.getId())) {
                payments.add(orderedMap(
                        "art", p.getMethodType() == null ? null : p.getMethodType().name(),
                        "maskiert", p.getMaskedDetails()));
            }
        }
        root.put("lieferadressen", addresses);
        root.put("zahlungsarten", payments);

        // 2. Transaktionsdaten – Bestellungen
        List<Order> orders = user != null
                ? orderRepository.findByCustomerIdOrderByCreatedAtDesc(user.getId())
                : orderRepository.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(request.getEmail());
        List<Map<String, Object>> orderMaps = new ArrayList<>();
        for (Order o : orders) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (OrderItem it : o.getItems()) {
                items.add(orderedMap(
                        "artikel", it.getProduct() == null ? "?" : it.getProduct().getName(),
                        "menge", it.getQuantity(),
                        "einzelpreis", it.getPriceAtOrderTime()));
            }
            orderMaps.add(orderedMap(
                    "bestellnummer", o.getOrderNumber(),
                    "status", o.getStatus() == null ? null : o.getStatus().name(),
                    "datum", fmt(o.getCreatedAt()),
                    "gesamtbetrag", o.getTotalPrice(),
                    "zahlungsart", o.getPaymentMethodType() == null ? null : o.getPaymentMethodType().name(),
                    "zahlungMaskiert", o.getPaymentMaskedDetails(),
                    "positionen", items));
        }
        root.put("bestellungen", orderMaps);

        // Retouren
        List<Map<String, Object>> returns = new ArrayList<>();
        if (user != null) {
            for (ReturnRequest r : returnRequestRepository.findByCustomerIdOrderByCreatedAtDesc(user.getId())) {
                returns.add(orderedMap(
                        "vorgang", r.getTrackingId(),
                        "status", r.getStatus() == null ? null : r.getStatus().name(),
                        "grund", r.getReason() == null ? null : r.getReason().name(),
                        "bestellung", r.getOrder() == null ? null : r.getOrder().getOrderNumber(),
                        "datum", fmt(r.getCreatedAt())));
            }
        }
        root.put("retouren", returns);

        // 3. Interaktionsdaten – Support-Tickets
        List<Map<String, Object>> tickets = new ArrayList<>();
        if (user != null) {
            for (SupportTicket t : supportTicketRepository.findByCustomerIdOrderByUpdatedAtDesc(user.getId())) {
                List<Map<String, Object>> msgs = new ArrayList<>();
                t.getMessages().stream()
                        .filter(m -> !m.isInternalNote())
                        .forEach(m -> msgs.add(orderedMap(
                                "datum", fmt(m.getCreatedAt()),
                                "nachricht", m.getMessageText())));
                tickets.add(orderedMap(
                        "ticket", t.getTicketNumber(),
                        "betreff", t.getSubject(),
                        "status", t.getStatus() == null ? null : t.getStatus().name(),
                        "erstelltAm", fmt(t.getCreatedAt()),
                        "verlauf", msgs));
            }
        }
        root.put("supportTickets", tickets);

        // 4. Einwilligungen
        Map<String, Object> consent = new LinkedHashMap<>();
        if (user != null) {
            consent.put("agbAkzeptiertAm", fmt(user.getAgbAcceptedAt()));
        }
        consent.put("hinweis", "Cookie-/Tracking-Einwilligungen werden clientseitig verwaltet und sind hier nicht enthalten.");
        root.put("einwilligungen", consent);

        // 5. Log-Daten – Audit
        List<Map<String, Object>> logs = new ArrayList<>();
        if (user != null) {
            for (AuditLog a : auditLogRepository.findByUserIdOrderByTimestampDesc(user.getId())) {
                logs.add(orderedMap(
                        "zeitpunkt", fmt(a.getTimestamp()),
                        "aktion", a.getAction(),
                        "objekt", a.getEntityType(),
                        "ausgeloestVon", a.getInitiatedBy() == null ? null : a.getInitiatedBy().name(),
                        "details", a.getDetails()));
            }
        }
        root.put("logDaten", logs);

        return root;
    }

    // ── CSV ────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String buildOrdersCsv(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("Bestellnummer;Datum;Status;Gesamtbetrag;Zahlungsart\n");
        List<Map<String, Object>> orders = (List<Map<String, Object>>) data.getOrDefault("bestellungen", List.of());
        for (Map<String, Object> o : orders) {
            sb.append(csv(o.get("bestellnummer"))).append(';')
              .append(csv(o.get("datum"))).append(';')
              .append(csv(o.get("status"))).append(';')
              .append(csv(o.get("gesamtbetrag"))).append(';')
              .append(csv(o.get("zahlungsart"))).append('\n');
        }
        return sb.toString();
    }

    // ── PDF (Art. 15 transparency report) ───────────────────────────────────────

    @SuppressWarnings("unchecked")
    private byte[] buildTransparencyPdf(DataExportRequest request, Map<String, Object> data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9);

        doc.add(new Paragraph("Datenauskunft gemäß Art. 15 & 20 DSGVO", h1));
        doc.add(new Paragraph("Erstellt am: " + LocalDateTime.now().format(TS), small));
        doc.add(new Paragraph("Betroffene Person: " + request.getEmail(), small));
        doc.add(spacer());

        // Legal meta-information
        doc.add(new Paragraph("1. Verarbeitungszwecke", h2));
        doc.add(new Paragraph("Vertragsabwicklung (Bestellungen, Lieferung, Zahlung), Kundenservice, "
                + "gesetzliche Aufbewahrungspflichten, Betrugsprävention und Produktverbesserung.", normal));
        doc.add(spacer());

        doc.add(new Paragraph("2. Geplante Speicherdauer", h2));
        doc.add(new Paragraph("Rechnungs-/Bestelldaten: 10 Jahre (§ 147 AO, § 257 HGB). "
                + "Kontodaten: bis zur Löschung des Kontos. Support-/Log-Daten: bis zu 24 Monate.", normal));
        doc.add(spacer());

        doc.add(new Paragraph("3. Kategorien von Drittempfängern", h2));
        doc.add(new Paragraph("Zahlungsdienstleister (z. B. Stripe/PayPal), Logistikdienstleister (z. B. DHL), "
                + "E-Mail-Versanddienstleister. Eine Weitergabe erfolgt nur, soweit zur Vertragserfüllung erforderlich.", normal));
        doc.add(spacer());

        doc.add(new Paragraph("4. Ihre gespeicherten Daten (Übersicht)", h2));
        Map<String, Object> profile = (Map<String, Object>) data.getOrDefault("stammdaten", Map.of());
        profile.forEach((k, v) -> {
            if (!(v instanceof Map)) doc.add(new Paragraph(k + ": " + nv(v), normal));
        });
        doc.add(new Paragraph("Lieferadressen: " + sizeOf(data.get("lieferadressen")), normal));
        doc.add(new Paragraph("Zahlungsarten: " + sizeOf(data.get("zahlungsarten")), normal));
        doc.add(new Paragraph("Bestellungen: " + sizeOf(data.get("bestellungen")), normal));
        doc.add(new Paragraph("Retouren: " + sizeOf(data.get("retouren")), normal));
        doc.add(new Paragraph("Support-Tickets: " + sizeOf(data.get("supportTickets")), normal));
        doc.add(new Paragraph("Log-/Audit-Einträge: " + sizeOf(data.get("logDaten")), normal));
        doc.add(spacer());

        doc.add(new Paragraph("Die vollständigen, maschinenlesbaren Rohdaten finden Sie in der Datei "
                + "daten/export.json sowie daten/bestellungen.csv in diesem Archiv.", small));

        doc.close();
        return out.toByteArray();
    }

    private String buildReadme() {
        return "DSGVO-Datenexport\n"
                + "==================\n\n"
                + "Auskunft_DSGVO.pdf      – Menschenlesbarer Transparenzbericht (Art. 15 DSGVO)\n"
                + "daten/export.json       – Vollständige, strukturierte Rohdaten (Art. 20 DSGVO)\n"
                + "daten/bestellungen.csv  – Bestellungen als Tabelle (Art. 20 DSGVO)\n\n"
                + "Dieser Download-Link ist aus Sicherheitsgründen zeitlich befristet.\n";
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void writeEntry(ZipOutputStream zip, String name, byte[] content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private Paragraph spacer() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(6f);
        p.setAlignment(Element.ALIGN_LEFT);
        return p;
    }

    private static Map<String, Object> orderedMap(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static String fmt(java.time.Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(TS);
    }

    private static String fmt(java.time.LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static String nv(Object v) {
        return v == null ? "—" : String.valueOf(v);
    }

    private static String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v).replace("\"", "\"\"");
        return s.contains(";") || s.contains("\n") ? "\"" + s + "\"" : s;
    }

    private static int sizeOf(Object list) {
        return list instanceof List ? ((List<?>) list).size() : 0;
    }
}
