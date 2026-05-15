package de.fhdw.webshop.quote;

import de.fhdw.webshop.cart.CartItem;
import de.fhdw.webshop.cart.CartRepository;
import de.fhdw.webshop.cart.CartService;
import de.fhdw.webshop.cart.dto.CartItemResponse;
import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.product.ProductType;
import de.fhdw.webshop.quote.dto.CreateQuoteRequest;
import de.fhdw.webshop.quote.dto.QuoteRequestItemResponse;
import de.fhdw.webshop.quote.dto.QuoteRequestResponse;
import de.fhdw.webshop.user.BusinessInfo;
import de.fhdw.webshop.user.BusinessInfoRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserType;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuoteRequestService {

    private static final int VALIDITY_DAYS = 14;
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            .withZone(DISPLAY_ZONE);

    private final QuoteRequestRepository quoteRequestRepository;
    private final CartRepository cartRepository;
    private final CartService cartService;
    private final ProductRepository productRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final EmailService emailService;

    @Transactional
    public QuoteRequestResponse createQuoteRequest(User customer, CreateQuoteRequest request) {
        requireBusinessCustomer(customer);

        CartResponse cart = cartService.getCart(customer.getId());
        if (cart.items().isEmpty()) {
            throw new IllegalArgumentException("Der Warenkorb ist leer.");
        }

        Instant now = Instant.now();
        BusinessInfo businessInfo = businessInfoRepository.findByUserId(customer.getId()).orElse(null);
        QuoteRequest quoteRequest = new QuoteRequest();
        quoteRequest.setQuoteNumber(createQuoteNumber(now));
        quoteRequest.setCustomer(customer);
        quoteRequest.setCustomerEmail(customer.getEmail());
        quoteRequest.setCustomerName(customer.getUsername());
        quoteRequest.setCompanyName(businessInfo == null ? null : businessInfo.getCompanyName());
        quoteRequest.setCompanyDetails(toCompanyDetails(businessInfo));
        quoteRequest.setNotes(normalizeNotes(request == null ? null : request.notes()));
        quoteRequest.setStatus(QuoteRequestStatus.REQUESTED);
        quoteRequest.setCreatedAt(now);
        quoteRequest.setValidUntil(now.plusSeconds(VALIDITY_DAYS * 24L * 60L * 60L));
        quoteRequest.setSubtotal(money(cart.subtotal()));
        quoteRequest.setDiscountAmount(money(cart.discountAmount()));
        quoteRequest.setTaxAmount(money(cart.tax()));
        quoteRequest.setShippingCost(money(cart.shippingCost()));
        quoteRequest.setTotalPrice(money(cart.total()));

        for (CartItemResponse cartItem : cart.items()) {
            Product product = productRepository.findById(cartItem.productId())
                    .orElseThrow(() -> new EntityNotFoundException("Product not found: " + cartItem.productId()));
            QuoteRequestItem item = new QuoteRequestItem();
            item.setQuoteRequest(quoteRequest);
            item.setProduct(product);
            item.setProductName(cartItem.productName());
            item.setQuantity(cartItem.quantity());
            item.setUnitPrice(money(cartItem.unitPrice()));
            item.setLineTotal(money(cartItem.lineTotal()));
            item.setPersonalizationText(cartItem.personalizationText());
            item.setGiftCardAmount(cartItem.giftCardAmount());
            item.setGiftCardRecipientEmail(cartItem.giftCardRecipientEmail());
            item.setGiftCardMessage(cartItem.giftCardMessage());
            quoteRequest.getItems().add(item);
        }

        quoteRequest.setPdfDocument(generatePdf(quoteRequest));
        QuoteRequest savedQuoteRequest = quoteRequestRepository.save(quoteRequest);
        cartService.clearCartSilently(customer.getId());
        sendConfirmationEmail(savedQuoteRequest);
        return toResponse(savedQuoteRequest);
    }

    @Transactional(readOnly = true)
    public List<QuoteRequestResponse> listQuoteRequests(User customer) {
        requireBusinessCustomer(customer);
        return quoteRequestRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CartResponse restoreQuoteToCart(User customer, Long quoteRequestId) {
        requireBusinessCustomer(customer);
        QuoteRequest quoteRequest = loadOwnedQuote(customer, quoteRequestId);
        if (isExpired(quoteRequest)) {
            throw new IllegalArgumentException("Dieses Angebot ist nicht mehr gueltig.");
        }
        if (quoteRequest.getItems().isEmpty()) {
            throw new IllegalArgumentException("Dieses Angebot enthaelt keine Artikel.");
        }

        cartService.clearCartSilently(customer.getId());
        for (QuoteRequestItem quoteItem : quoteRequest.getItems()) {
            Product product = productRepository.findById(quoteItem.getProduct().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Product not found: " + quoteItem.getProduct().getId()));
            validateProductCanBeRestored(product, quoteItem.getQuantity());

            CartItem cartItem = cartRepository
                    .findByUserIdAndProductIdAndPersonalizationTextAndGiftCardAmountAndGiftCardRecipientEmailAndGiftCardMessageAndSharedWishlistTokenAndSharedWishlistListId(
                            customer.getId(),
                            product.getId(),
                            quoteItem.getPersonalizationText(),
                            quoteItem.getGiftCardAmount(),
                            quoteItem.getGiftCardRecipientEmail(),
                            quoteItem.getGiftCardMessage(),
                            null,
                            null)
                    .orElseGet(() -> {
                        CartItem newCartItem = new CartItem();
                        newCartItem.setUser(customer);
                        newCartItem.setProduct(product);
                        newCartItem.setQuantity(0);
                        newCartItem.setPersonalizationText(quoteItem.getPersonalizationText());
                        newCartItem.setGiftCardAmount(quoteItem.getGiftCardAmount());
                        newCartItem.setGiftCardRecipientEmail(quoteItem.getGiftCardRecipientEmail());
                        newCartItem.setGiftCardMessage(quoteItem.getGiftCardMessage());
                        return newCartItem;
                    });
            cartItem.setQuantity(cartItem.getQuantity() + quoteItem.getQuantity());
            cartRepository.save(cartItem);
        }

        quoteRequest.setStatus(QuoteRequestStatus.CONVERTED);
        quoteRequestRepository.save(quoteRequest);
        return cartService.getCart(customer.getId());
    }

    @Transactional(readOnly = true)
    public QuoteRequest loadOwnedQuote(User customer, Long quoteRequestId) {
        requireBusinessCustomer(customer);
        return quoteRequestRepository.findByIdAndCustomerId(quoteRequestId, customer.getId())
                .orElseThrow(() -> new EntityNotFoundException("Angebot nicht gefunden: " + quoteRequestId));
    }

    private void validateProductCanBeRestored(Product product, int quantity) {
        if (!product.isPurchasable()) {
            throw new IllegalArgumentException("Der Artikel " + product.getName() + " ist aktuell nicht bestellbar.");
        }
        if (product.getProductType() != ProductType.DIGITAL_GIFT_CARD && product.getStock() < quantity) {
            throw new IllegalArgumentException("Der Bestand reicht fuer " + product.getName() + " nicht aus.");
        }
    }

    private void requireBusinessCustomer(User user) {
        if (user == null || user.getUserType() != UserType.BUSINESS) {
            throw new IllegalArgumentException("Angebotsanforderungen sind nur fuer verifizierte B2B-Kunden verfuegbar.");
        }
    }

    private String createQuoteNumber(Instant now) {
        String datePart = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(DISPLAY_ZONE).format(now);
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "ANG-" + datePart + "-" + (int) (1000 + Math.random() * 9000);
            if (!quoteRequestRepository.existsByQuoteNumber(candidate)) {
                return candidate;
            }
        }
        return "ANG-" + datePart + "-" + System.currentTimeMillis();
    }

    private String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        return notes.trim();
    }

    private String toCompanyDetails(BusinessInfo businessInfo) {
        if (businessInfo == null) {
            return null;
        }
        List<String> details = new ArrayList<>();
        if (businessInfo.getIndustry() != null && !businessInfo.getIndustry().isBlank()) {
            details.add("Branche: " + businessInfo.getIndustry());
        }
        if (businessInfo.getCompanySize() != null && !businessInfo.getCompanySize().isBlank()) {
            details.add("Groesse: " + businessInfo.getCompanySize());
        }
        return details.isEmpty() ? null : String.join(", ", details);
    }

    private void sendConfirmationEmail(QuoteRequest quoteRequest) {
        String body = """
                Ihre Angebotsanforderung wurde erstellt.

                Angebotsnummer: %s
                Gueltig bis: %s
                Gesamtbetrag: %s

                Das Angebot ist im Kundenkonto unter "Meine Angebote" abrufbar und kann innerhalb der Gueltigkeitsfrist wieder in den Warenkorb uebernommen werden.
                """.formatted(
                quoteRequest.getQuoteNumber(),
                DATE_FORMATTER.format(quoteRequest.getValidUntil()),
                formatMoney(quoteRequest.getTotalPrice()));
        emailService.sendEmail(quoteRequest.getCustomerEmail(), "Ihr Webshop-Angebot " + quoteRequest.getQuoteNumber(), body);
    }

    private QuoteRequestResponse toResponse(QuoteRequest quoteRequest) {
        boolean expired = isExpired(quoteRequest);
        String status = expired && quoteRequest.getStatus() == QuoteRequestStatus.REQUESTED
                ? "EXPIRED"
                : quoteRequest.getStatus().name();
        return new QuoteRequestResponse(
                quoteRequest.getId(),
                quoteRequest.getQuoteNumber(),
                quoteRequest.getCreatedAt(),
                quoteRequest.getValidUntil(),
                status,
                expired,
                quoteRequest.getNotes(),
                quoteRequest.getCompanyName(),
                quoteRequest.getSubtotal(),
                quoteRequest.getDiscountAmount(),
                quoteRequest.getTaxAmount(),
                quoteRequest.getShippingCost(),
                quoteRequest.getTotalPrice(),
                "/api/quote-requests/" + quoteRequest.getId() + "/pdf",
                quoteRequest.getItems().stream()
                        .map(item -> new QuoteRequestItemResponse(
                                item.getId(),
                                item.getProduct().getId(),
                                item.getProductName(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getLineTotal()))
                        .toList()
        );
    }

    private boolean isExpired(QuoteRequest quoteRequest) {
        return quoteRequest.getValidUntil() != null && quoteRequest.getValidUntil().isBefore(Instant.now());
    }

    private BigDecimal money(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMoney(BigDecimal value) {
        return money(value).toPlainString().replace('.', ',') + " EUR";
    }

    private byte[] generatePdf(QuoteRequest quoteRequest) {
        List<String> lines = new ArrayList<>();
        lines.add("Webshop Angebot");
        lines.add("Angebotsnummer: " + quoteRequest.getQuoteNumber());
        lines.add("Erstellt am: " + DATE_FORMATTER.format(quoteRequest.getCreatedAt()));
        lines.add("Gueltig bis: " + DATE_FORMATTER.format(quoteRequest.getValidUntil()));
        lines.add("");
        lines.add("Kunde: " + quoteRequest.getCustomerName());
        lines.add("E-Mail: " + quoteRequest.getCustomerEmail());
        lines.add("Firma: " + blankToDash(quoteRequest.getCompanyName()));
        lines.add("Firmendaten: " + blankToDash(quoteRequest.getCompanyDetails()));
        if (quoteRequest.getNotes() != null) {
            lines.add("Anmerkung: " + quoteRequest.getNotes());
        }
        lines.add("");
        lines.add("Positionen:");
        for (QuoteRequestItem item : quoteRequest.getItems()) {
            lines.add(item.getQuantity() + "x " + item.getProductName()
                    + " | Einzelpreis " + formatMoney(item.getUnitPrice())
                    + " | Gesamt " + formatMoney(item.getLineTotal()));
        }
        lines.add("");
        lines.add("Zwischensumme: " + formatMoney(quoteRequest.getSubtotal()));
        if (quoteRequest.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            lines.add("Rabatt: -" + formatMoney(quoteRequest.getDiscountAmount()));
        }
        lines.add("MwSt.: " + formatMoney(quoteRequest.getTaxAmount()));
        lines.add("Versand: " + formatMoney(quoteRequest.getShippingCost()));
        lines.add("Gesamtbetrag: " + formatMoney(quoteRequest.getTotalPrice()));
        lines.add("");
        lines.add("Dieses Angebot kann innerhalb der Gueltigkeitsfrist im Kundenkonto in einen Warenkorb uebernommen werden.");
        return PdfDocumentBuilder.build(lines);
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static final class PdfDocumentBuilder {

        private PdfDocumentBuilder() {
        }

        static byte[] build(List<String> rawLines) {
            List<String> lines = rawLines.stream()
                    .flatMap(line -> wrap(asciiSafe(line), 105).stream())
                    .toList();
            StringBuilder content = new StringBuilder();
            content.append("BT\n/F1 18 Tf\n50 790 Td\n(Webshop Angebot) Tj\n");
            content.append("/F1 10 Tf\n0 -22 Td\n");
            boolean first = true;
            for (String line : lines.stream().skip(1).toList()) {
                if (!first) {
                    content.append("0 -14 Td\n");
                }
                first = false;
                content.append("(").append(escape(line)).append(") Tj\n");
            }
            content.append("ET\n");

            String stream = content.toString();
            List<String> objects = List.of(
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                    "<< /Length " + stream.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + stream + "endstream"
            );

            StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
            List<Integer> offsets = new ArrayList<>();
            for (int index = 0; index < objects.size(); index++) {
                offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
                pdf.append(index + 1).append(" 0 obj\n")
                        .append(objects.get(index))
                        .append("\nendobj\n");
            }

            int xrefOffset = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
            pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
            pdf.append("0000000000 65535 f \n");
            for (Integer offset : offsets) {
                pdf.append(String.format("%010d 00000 n \n", offset));
            }
            pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
            pdf.append("startxref\n").append(xrefOffset).append("\n%%EOF");
            return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
        }

        private static List<String> wrap(String value, int maxLength) {
            if (value == null || value.length() <= maxLength) {
                return List.of(value == null ? "" : value);
            }
            List<String> lines = new ArrayList<>();
            String remaining = value;
            while (remaining.length() > maxLength) {
                int splitAt = remaining.lastIndexOf(' ', maxLength);
                if (splitAt < 1) {
                    splitAt = maxLength;
                }
                lines.add(remaining.substring(0, splitAt).trim());
                remaining = remaining.substring(splitAt).trim();
            }
            lines.add(remaining);
            return lines;
        }

        private static String asciiSafe(String value) {
            return value == null ? "" : value
                    .replace("ä", "ae")
                    .replace("ö", "oe")
                    .replace("ü", "ue")
                    .replace("Ä", "Ae")
                    .replace("Ö", "Oe")
                    .replace("Ü", "Ue")
                    .replace("ß", "ss");
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\")
                    .replace("(", "\\(")
                    .replace(")", "\\)");
        }
    }
}
