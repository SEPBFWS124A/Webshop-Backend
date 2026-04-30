package de.fhdw.webshop.product;

import de.fhdw.webshop.customer.StatisticsService;
import de.fhdw.webshop.product.dto.*;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductService.DiscountLookupPort discountLookupPort;
    private final StatisticsService statisticsService;

    /**
     * US #8 — Customers see only purchasable products.
     * US #10, #20 — Employees see all products (pass purchasable=false or omit).
     * US #46 — Search by term.
     * US #47 — Filter by category.
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> listProducts(
            @RequestParam(required = false) Boolean purchasable,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "ecoScore") List<ProductEcoScore> ecoScores) {
        return ResponseEntity.ok(productService.listProducts(purchasable, category, search, ecoScores));
    }

    /** US #23, #25, #28 — Single product detail (description, image, price). */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    /** US #31 — Price after applying customer-specific discounts. */
    @GetMapping("/{id}/price-for-customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ProductPriceResponse> getPriceForCustomer(@PathVariable Long id,
                                                                    @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(productService.getPriceForCustomer(id, currentUser, discountLookupPort));
    }

    /** US #13 — Create a new product. */
    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(productRequest));
    }

    /** US #14 — Delete a product. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    /** US #15 — Set purchasable flag. */
    @PutMapping("/{id}/purchasable")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<ProductResponse> setPurchasable(@PathVariable Long id,
                                                          @Valid @RequestBody SetFlagRequest setFlagRequest) {
        return ResponseEntity.ok(productService.setPurchasable(id, setFlagRequest.value()));
    }

    /** US #26 — Set promoted flag. */
    @PutMapping("/{id}/promoted")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<ProductResponse> setPromoted(@PathVariable Long id,
                                                       @Valid @RequestBody SetFlagRequest setFlagRequest) {
        return ResponseEntity.ok(productService.setPromoted(id, setFlagRequest.value()));
    }

    /** US #16 — Update description. */
    @PutMapping("/{id}/description")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<ProductResponse> updateDescription(@PathVariable Long id,
                                                             @Valid @RequestBody UpdateDescriptionRequest updateDescriptionRequest) {
        return ResponseEntity.ok(productService.updateDescription(id, updateDescriptionRequest));
    }

    /** US #17 — Update image. */
    @PutMapping("/{id}/image")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<ProductResponse> updateImage(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateImageRequest updateImageRequest) {
        return ResponseEntity.ok(productService.updateImage(id, updateImageRequest));
    }

    /** US #18 — Update recommended retail price (sales employees only). */
    @PutMapping("/{id}/price")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<ProductResponse> updatePrice(@PathVariable Long id,
                                                       @Valid @RequestBody UpdatePriceRequest updatePriceRequest) {
        return ResponseEntity.ok(productService.updatePrice(id, updatePriceRequest));
    }

    /** US #34 — Sales statistics (units sold, revenue) for a product over a date range. */
    /** US #196 — Update estimated product CO2 footprint in kg CO2e. */
    @PutMapping("/{id}/co2-emission")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<ProductResponse> updateCo2Emission(@PathVariable Long id,
                                                             @Valid @RequestBody UpdateCo2EmissionRequest updateCo2EmissionRequest) {
        return ResponseEntity.ok(productService.updateCo2Emission(id, updateCo2EmissionRequest));
    }

    /** US #198 - Update Eco-Score. */
    @PutMapping("/{id}/eco-score")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<ProductResponse> updateEcoScore(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateEcoScoreRequest updateEcoScoreRequest) {
        return ResponseEntity.ok(productService.updateEcoScore(id, updateEcoScoreRequest));
    }

    @GetMapping("/{id}/statistics")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<ProductStatisticsResponse> getProductStatistics(
            @PathVariable Long id,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(statisticsService.getProductStatistics(id, from, to));
    }
}
