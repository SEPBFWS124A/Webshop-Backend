package de.fhdw.webshop.user;

import de.fhdw.webshop.user.dto.PaymentMethodRequest;
import java.time.Year;

public final class PaymentMethodSupport {

    private static final String VISA = "Visa";
    private static final String AMEX = "American Express";

    private PaymentMethodSupport() {
    }

    public static String toStoredDetails(PaymentMethodRequest request) {
        validate(request);
        if (request.methodType() == PaymentMethodType.CREDIT_CARD) {
            String digitsOnly = normalizeDigits(request.cardNumber());
            if (digitsOnly.length() >= 12) {
                String lastFourDigits = digitsOnly.substring(digitsOnly.length() - 4);
                return detectCardBrandLabel(digitsOnly) + " **** " + lastFourDigits + " | " + formatExpiry(request.expiryMonth(), request.expiryYear());
            }
            return request.maskedDetails().trim();
        }
        return request.maskedDetails().trim();
    }

    public static void validate(PaymentMethodRequest request) {
        if (request == null || request.methodType() == null) {
            throw new IllegalArgumentException("Eine Zahlungsart ist erforderlich");
        }

        if (request.methodType() == PaymentMethodType.CREDIT_CARD) {
            String digitsOnly = normalizeDigits(request.cardNumber());
            boolean hasFullCardNumber = digitsOnly.length() >= 12 && digitsOnly.length() <= 19;
            boolean hasStoredMaskedCard = request.maskedDetails() != null && request.maskedDetails().trim().startsWith("****");
            boolean hasStoredBrandedCard = request.maskedDetails() != null
                    && (request.maskedDetails().contains(VISA) || request.maskedDetails().contains(AMEX));
            if (!hasFullCardNumber && !hasStoredMaskedCard && !hasStoredBrandedCard) {
                throw new IllegalArgumentException("Die Kartennummer muss 12 bis 19 Ziffern enthalten");
            }
            String brandLabel = hasFullCardNumber ? detectCardBrandLabel(digitsOnly) : detectCardBrandLabel(request.maskedDetails());
            if (hasFullCardNumber && brandLabel == null) {
                throw new IllegalArgumentException("Es werden nur Visa und American Express akzeptiert");
            }
            if (hasFullCardNumber && !isValidLuhn(digitsOnly)) {
                throw new IllegalArgumentException("Die Kartennummer ist ungueltig");
            }
            if (request.expiryMonth() == null || request.expiryMonth() < 1 || request.expiryMonth() > 12) {
                throw new IllegalArgumentException("Der Ablaufmonat muss zwischen 1 und 12 liegen");
            }
            int currentYear = Year.now().getValue();
            if (request.expiryYear() == null || request.expiryYear() < currentYear || request.expiryYear() > currentYear + 20) {
                throw new IllegalArgumentException("Das Ablaufjahr ist ungueltig");
            }
            if (request.expiryYear() == currentYear && request.expiryMonth() < java.time.LocalDate.now().getMonthValue()) {
                throw new IllegalArgumentException("Die Kreditkarte ist bereits abgelaufen");
            }
            String cvvDigits = normalizeDigits(request.cvv());
            int expectedCvvLength = AMEX.equals(brandLabel) ? 4 : 3;
            if (cvvDigits.length() != expectedCvvLength) {
                throw new IllegalArgumentException(AMEX.equals(brandLabel)
                        ? "American Express benoetigt einen 4-stelligen Sicherheitscode"
                        : "Visa benoetigt einen 3-stelligen Sicherheitscode");
            }
            return;
        }

        if (request.maskedDetails() == null || request.maskedDetails().isBlank()) {
            throw new IllegalArgumentException("Bitte gib Zahlungsdetails an");
        }
    }

    public static String buildPreviewLabel(PaymentMethodRequest request) {
        return switch (request.methodType()) {
            case CREDIT_CARD -> buildCreditCardPreview(request);
            case SEPA_DIRECT_DEBIT -> "SEPA-Lastschrift " + request.maskedDetails().trim();
            case BANK_TRANSFER -> "Banküberweisung " + request.maskedDetails().trim();
        };
    }

    private static String buildCreditCardPreview(PaymentMethodRequest request) {
        String digitsOnly = normalizeDigits(request.cardNumber());
        String brandLabel = digitsOnly.length() >= 12
                ? detectCardBrandLabel(digitsOnly)
                : detectCardBrandLabel(request.maskedDetails());
        String lastFourDigits = digitsOnly.length() >= 4
                ? digitsOnly.substring(digitsOnly.length() - 4)
                : extractLastFourDigits(request.maskedDetails());
        return (brandLabel != null ? brandLabel : "Kreditkarte")
                + " •••• "
                + lastFourDigits
                + " · "
                + formatExpiry(request.expiryMonth(), request.expiryYear());
    }

    private static String formatExpiry(Integer month, Integer year) {
        return String.format("%02d/%d", month, year);
    }

    private static String normalizeDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String detectCardBrandLabel(String value) {
        String digitsOnly = normalizeDigits(value);
        if (digitsOnly.startsWith("34") || digitsOnly.startsWith("37")) {
            return AMEX;
        }
        if (digitsOnly.startsWith("4")) {
            return VISA;
        }
        if (value != null && value.contains(AMEX)) {
            return AMEX;
        }
        if (value != null && value.contains(VISA)) {
            return VISA;
        }
        return null;
    }

    private static String extractLastFourDigits(String maskedDetails) {
        if (maskedDetails == null) {
            return "----";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{4})").matcher(maskedDetails);
        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group(1);
        }
        return lastMatch != null ? lastMatch : "----";
    }

    private static boolean isValidLuhn(String digitsOnly) {
        int sum = 0;
        boolean shouldDouble = false;
        for (int index = digitsOnly.length() - 1; index >= 0; index--) {
            int digit = Character.digit(digitsOnly.charAt(index), 10);
            if (shouldDouble) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            shouldDouble = !shouldDouble;
        }
        return sum % 10 == 0;
    }
}
