package de.fhdw.webshop.discount;

import de.fhdw.webshop.discount.dto.VolumeDiscountActiveRequest;
import de.fhdw.webshop.discount.dto.VolumeDiscountTierRequest;
import de.fhdw.webshop.discount.dto.VolumeDiscountTierResponse;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/volume-discount-tiers")
@PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
@RequiredArgsConstructor
public class VolumeDiscountTierController {

    private final VolumeDiscountService volumeDiscountService;

    @GetMapping
    public ResponseEntity<List<VolumeDiscountTierResponse>> listTiers() {
        return ResponseEntity.ok(volumeDiscountService.listAll());
    }

    @PostMapping
    public ResponseEntity<VolumeDiscountTierResponse> createTier(
            @Valid @RequestBody VolumeDiscountTierRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(volumeDiscountService.create(request, currentUser));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VolumeDiscountTierResponse> updateTier(
            @PathVariable Long id,
            @Valid @RequestBody VolumeDiscountTierRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(volumeDiscountService.update(id, request, currentUser));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<VolumeDiscountTierResponse> setTierActive(
            @PathVariable Long id,
            @Valid @RequestBody VolumeDiscountActiveRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(volumeDiscountService.setActive(id, request.value(), currentUser));
    }
}
