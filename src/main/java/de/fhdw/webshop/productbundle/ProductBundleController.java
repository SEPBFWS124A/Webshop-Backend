package de.fhdw.webshop.productbundle;

import de.fhdw.webshop.productbundle.dto.ProductBundleResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-bundles")
@RequiredArgsConstructor
public class ProductBundleController {

    private final ProductBundleService productBundleService;

    @GetMapping("/active")
    public ResponseEntity<List<ProductBundleResponse>> listActiveBundles(
            @RequestParam(required = false) Long productId) {
        return ResponseEntity.ok(productBundleService.listActiveBundles(productId));
    }
}
