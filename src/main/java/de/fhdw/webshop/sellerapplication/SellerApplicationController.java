package de.fhdw.webshop.sellerapplication;

import de.fhdw.webshop.sellerapplication.dto.CreateSellerApplicationRequest;
import de.fhdw.webshop.sellerapplication.dto.SellerApplicationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seller-applications")
@RequiredArgsConstructor
public class SellerApplicationController {

    private final SellerApplicationService sellerApplicationService;

    /** US #247 — Potenzielle Verkäufer können ohne Login einen Antrag absenden. */
    @PostMapping
    public ResponseEntity<SellerApplicationResponse> createSellerApplication(
            @Valid @RequestBody CreateSellerApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sellerApplicationService.create(request));
    }
}
