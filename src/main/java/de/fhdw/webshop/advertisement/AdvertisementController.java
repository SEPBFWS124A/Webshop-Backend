package de.fhdw.webshop.advertisement;

import de.fhdw.webshop.advertisement.dto.AdvertisementActiveRequest;
import de.fhdw.webshop.advertisement.dto.AdvertisementRequest;
import de.fhdw.webshop.advertisement.dto.AdvertisementResponse;
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
@RequiredArgsConstructor
public class AdvertisementController {

    private final AdvertisementService advertisementService;

    @GetMapping("/api/advertisements/active")
    public ResponseEntity<List<AdvertisementResponse>> listActiveAdvertisements() {
        return ResponseEntity.ok(advertisementService.listActive());
    }

    @GetMapping("/api/admin/advertisements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdvertisementResponse>> listAllAdvertisements() {
        return ResponseEntity.ok(advertisementService.listAll());
    }

    @PostMapping("/api/admin/advertisements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdvertisementResponse> createAdvertisement(
            @Valid @RequestBody AdvertisementRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(advertisementService.create(request, currentUser));
    }

    @PutMapping("/api/admin/advertisements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdvertisementResponse> updateAdvertisement(
            @PathVariable Long id,
            @Valid @RequestBody AdvertisementRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(advertisementService.update(id, request, currentUser));
    }

    @PatchMapping("/api/admin/advertisements/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdvertisementResponse> setAdvertisementActive(
            @PathVariable Long id,
            @Valid @RequestBody AdvertisementActiveRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(advertisementService.setActive(id, request.value(), currentUser));
    }

    @DeleteMapping("/api/admin/advertisements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAdvertisement(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        advertisementService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
