package de.fhdw.webshop.sellerapplication;

import de.fhdw.webshop.sellerapplication.dto.CreateSellerApplicationRequest;
import de.fhdw.webshop.sellerapplication.dto.SellerApplicationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SellerApplicationController {

    private final SellerApplicationService sellerApplicationService;

    /** US #247 — Potenzielle Verkäufer können ohne Login einen Antrag absenden. */
    @PostMapping("/api/seller-applications")
    public ResponseEntity<SellerApplicationResponse> createSellerApplication(
            @Valid @RequestBody CreateSellerApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sellerApplicationService.create(request));
    }

    /** US #261 — Administratoren können eingegangene Verkäuferanträge einsehen. */
    @GetMapping("/api/admin/seller-applications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SellerApplicationResponse>> listSellerApplications() {
        return ResponseEntity.ok(sellerApplicationService.listAll());
    }

}
