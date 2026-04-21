package de.fhdw.webshop.address;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class AddressLookupService {

    private final RestClient postalLookupClient = RestClient.builder()
            .baseUrl("https://openplzapi.org")
            .build();
    private final RestClient addressLookupClient = RestClient.builder()
            .baseUrl("https://nominatim.openstreetmap.org")
            .defaultHeader("User-Agent", "Webshop Checkout/1.0")
            .build();

    public Optional<PostalCodeLookupResponse> lookupGermanPostalCode(String postalCode) {
        if (postalCode == null || !postalCode.matches("\\d{5}")) {
            return Optional.empty();
        }

        List<?> response;
        try {
            response = postalLookupClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/de/Localities")
                            .queryParam("postalCode", postalCode)
                            .build())
                    .retrieve()
                    .body(List.class);
        } catch (Exception exception) {
            log.warn("Postal code lookup failed for {}: {}", postalCode, exception.getMessage());
            return Optional.empty();
        }

        if (response == null || response.isEmpty() || !(response.get(0) instanceof Map<?, ?> localityMap)) {
            return Optional.empty();
        }

        Object cityValue = localityMap.get("name");
        if (!(cityValue instanceof String city) || city.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new PostalCodeLookupResponse(postalCode, city));
    }

    public List<AddressSuggestionResponse> suggestAddresses(String street, String postalCode, String city, String country) {
        String normalizedStreet = normalizeInput(street);
        String normalizedPostalCode = normalizePostalCode(postalCode);
        String normalizedCity = normalizeInput(city);
        String normalizedCountry = normalizeCountry(country);

        if (normalizedStreet.length() < 5) {
            return List.of();
        }

        // Only search once the user entered enough address detail to avoid irrelevant Germany-wide road matches.
        boolean hasHouseNumber = normalizedStreet.matches(".*\\d+[a-zA-Z]?$");
        boolean hasLocalityHint = !normalizedPostalCode.isBlank() || !normalizedCity.isBlank();
        if (!hasHouseNumber && !hasLocalityHint) {
            return List.of();
        }

        List<AddressSuggestionResponse> structuredResults = searchStructuredAddress(
                normalizedStreet,
                normalizedPostalCode,
                normalizedCity,
                normalizedCountry
        );
        if (!structuredResults.isEmpty()) {
            return structuredResults;
        }

        String query = List.of(normalizedStreet, normalizedPostalCode, normalizedCity, normalizedCountry)
                .stream()
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(", "));

        List<?> response;
        try {
            response = addressLookupClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("countrycodes", "de")
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", 1)
                            .queryParam("limit", 5)
                            .build())
                    .retrieve()
                    .body(List.class);
        } catch (Exception exception) {
            log.warn("Fallback address suggestion lookup failed for street='{}', postalCode='{}', city='{}': {}",
                    street, postalCode, city, exception.getMessage());
            return List.of();
        }

        if (response == null || response.isEmpty()) {
            return List.of();
        }

        return response.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::toSuggestionResponse)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .toList();
    }

    private List<AddressSuggestionResponse> searchStructuredAddress(String street,
                                                                    String postalCode,
                                                                    String city,
                                                                    String country) {
        List<?> response;
        try {
            response = addressLookupClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/search")
                                .queryParam("format", "jsonv2")
                                .queryParam("addressdetails", 1)
                                .queryParam("limit", 5)
                                .queryParam("countrycodes", "de")
                                .queryParam("street", street);

                        if (!postalCode.isBlank()) {
                            uriBuilder.queryParam("postalcode", postalCode);
                        }
                        if (!city.isBlank()) {
                            uriBuilder.queryParam("city", city);
                        }

                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(List.class);
        } catch (Exception exception) {
            log.warn("Structured address suggestion lookup failed for street='{}', postalCode='{}', city='{}': {}",
                    street, postalCode, city, exception.getMessage());
            return List.of();
        }

        if (response == null || response.isEmpty()) {
            return List.of();
        }

        return response.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::toSuggestionResponse)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .toList();
    }

    public AddressValidationResponse validateAddress(AddressValidationRequest request) {
        if (request == null) {
            return invalid("Die Lieferadresse ist unvollständig.");
        }

        String street = normalizeInput(request.street());
        String postalCode = normalizePostalCode(request.postalCode());
        String city = normalizeInput(request.city());
        String country = normalizeCountry(request.country());

        if (street.isBlank() || postalCode.isBlank() || city.isBlank()) {
            return invalid("Bitte Straße, PLZ und Ort vollständig angeben.");
        }

        List<AddressSuggestionResponse> suggestions;
        try {
            suggestions = suggestAddresses(street, postalCode, city, country);
        } catch (Exception exception) {
            log.warn("Address validation lookup failed for '{}', '{} {}': {}", street, postalCode, city, exception.getMessage());
            return invalid("Die Lieferadresse konnte technisch nicht geprüft werden. Du kannst sie bei Bedarf trotzdem bestätigen.");
        }
        return suggestions.stream()
                .filter(suggestion -> matchesAddress(suggestion, street, postalCode, city, country))
                .findFirst()
                .map(suggestion -> new AddressValidationResponse(
                        true,
                        suggestion.displayLabel(),
                        suggestion.street(),
                        suggestion.postalCode(),
                        suggestion.city(),
                        suggestion.country(),
                        "Adresse bestätigt"))
                .orElseGet(() -> invalid("Die Adresse konnte nicht eindeutig gefunden werden. Bitte wähle einen vorgeschlagenen Eintrag oder prüfe deine Angaben."));
    }

    private Optional<AddressSuggestionResponse> toSuggestionResponse(Map<?, ?> resultMap) {
        Object addressValue = resultMap.get("address");
        if (!(addressValue instanceof Map<?, ?> addressMap)) {
            return Optional.empty();
        }

        String road = asString(addressMap.get("road"));
        String houseNumber = asString(addressMap.get("house_number"));
        String street = joinParts(road, houseNumber);
        String postalCode = asString(addressMap.get("postcode"));
        String city = firstNonBlank(
                asString(addressMap.get("city")),
                asString(addressMap.get("town")),
                asString(addressMap.get("village")),
                asString(addressMap.get("municipality"))
        );
        String country = firstNonBlank(asString(addressMap.get("country")), "Germany");
        String displayLabel = firstNonBlank(asString(resultMap.get("display_name")), joinParts(street, postalCode + " " + city));

        if (street.isBlank() || postalCode.isBlank() || city.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new AddressSuggestionResponse(displayLabel, street, postalCode, city, country));
    }

    private boolean matchesAddress(AddressSuggestionResponse suggestion,
                                   String street,
                                   String postalCode,
                                   String city,
                                   String country) {
        String normalizedSuggestedStreet = normalizeComparable(suggestion.street());
        String normalizedStreet = normalizeComparable(street);
        String normalizedSuggestedCity = normalizeComparable(suggestion.city());
        String normalizedCity = normalizeComparable(city);

        boolean streetMatches = normalizedSuggestedStreet.equals(normalizedStreet)
                || normalizedSuggestedStreet.contains(normalizedStreet)
                || normalizedStreet.contains(normalizedSuggestedStreet);
        boolean cityMatches = normalizedSuggestedCity.equals(normalizedCity)
                || normalizedSuggestedCity.contains(normalizedCity)
                || normalizedCity.contains(normalizedSuggestedCity);

        return streetMatches
                && normalizePostalCode(suggestion.postalCode()).equals(postalCode)
                && cityMatches
                && (country.isBlank() || normalizeComparable(suggestion.country()).equals(normalizeComparable(country)));
    }

    private AddressValidationResponse invalid(String message) {
        return new AddressValidationResponse(false, null, null, null, null, null, message);
    }

    private String normalizeInput(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizePostalCode(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String normalizeCountry(String value) {
        String normalized = normalizeInput(value);
        if (normalized.isBlank()) {
            return "Germany";
        }
        if ("deutschland".equalsIgnoreCase(normalized)) {
            return "Germany";
        }
        return normalized;
    }

    private String normalizeComparable(String value) {
        return normalizeInput(value)
                .toLowerCase()
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
                .replaceAll("[^a-z0-9]", "");
    }

    private String asString(Object value) {
        return value instanceof String stringValue ? stringValue.trim() : "";
    }

    private String joinParts(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null ? "" : second.trim();
        }
        if (second == null || second.isBlank()) {
            return first.trim();
        }
        return first.trim() + " " + second.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
