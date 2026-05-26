package de.fhdw.webshop.productbundle;

import de.fhdw.webshop.productbundle.dto.ProductBundleRequest;
import de.fhdw.webshop.productbundle.dto.ProductBundleResponse;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/product-bundles")
@PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
@RequiredArgsConstructor
public class AdminProductBundleController {

    private final ProductBundleService productBundleService;

    @GetMapping
    public ResponseEntity<List<ProductBundleResponse>> listBundles() {
        return ResponseEntity.ok(productBundleService.listAllBundles());
    }

    @GetMapping("/{bundleId}")
    public ResponseEntity<ProductBundleResponse> getBundle(@PathVariable Long bundleId) {
        return ResponseEntity.ok(productBundleService.getBundle(bundleId));
    }

    @PostMapping
    public ResponseEntity<ProductBundleResponse> createBundle(
            @Valid @RequestBody ProductBundleRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productBundleService.createBundle(request, currentUser));
    }

    @PutMapping("/{bundleId}")
    public ResponseEntity<ProductBundleResponse> updateBundle(
            @PathVariable Long bundleId,
            @Valid @RequestBody ProductBundleRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(productBundleService.updateBundle(bundleId, request, currentUser));
    }

    @DeleteMapping("/{bundleId}")
    public ResponseEntity<Void> deleteBundle(
            @PathVariable Long bundleId,
            @AuthenticationPrincipal User currentUser) {
        productBundleService.deleteBundle(bundleId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
