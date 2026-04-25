package de.fhdw.webshop.address;

import java.util.Optional;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/address-lookup")
@RequiredArgsConstructor
public class AddressLookupController {

    private final AddressLookupService addressLookupService;

    @GetMapping
    public ResponseEntity<PostalCodeLookupResponse> lookupPostalCode(@RequestParam String postalCode) {
        Optional<PostalCodeLookupResponse> response = addressLookupService.lookupGermanPostalCode(postalCode);
        return response.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<AddressSuggestionResponse>> suggestAddresses(@RequestParam String street,
                                                                           @RequestParam(required = false) String postalCode,
                                                                           @RequestParam(required = false) String city,
                                                                           @RequestParam(required = false) String country) {
        return ResponseEntity.ok(addressLookupService.suggestAddresses(street, postalCode, city, country));
    }

    @GetMapping("/geocode")
    public ResponseEntity<GeocodedAddressResponse> geocodeAddress(@RequestParam(required = false) String street,
                                                                  @RequestParam(required = false) String postalCode,
                                                                  @RequestParam(required = false) String city,
                                                                  @RequestParam(required = false) String country) {
        return addressLookupService.geocodeAddress(street, postalCode, city, country)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/route")
    public ResponseEntity<RoadRouteResponse> route(@Valid @RequestBody RoadRouteRequest request) {
        return addressLookupService.routeTrip(request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/validate")
    public ResponseEntity<AddressValidationResponse> validateAddress(@RequestBody AddressValidationRequest request) {
        return ResponseEntity.ok(addressLookupService.validateAddress(request));
    }
}
