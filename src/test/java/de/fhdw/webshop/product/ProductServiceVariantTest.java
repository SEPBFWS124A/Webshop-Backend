package de.fhdw.webshop.product;

import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.product.dto.ProductRequest;
import de.fhdw.webshop.product.dto.ProductResponse;
import de.fhdw.webshop.product.dto.ProductVariantAttributeRequest;
import de.fhdw.webshop.product.dto.ProductVariantRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductServiceVariantTest {

    @Test
    void createProductGeneratesVariantMatrixFromAttributes() {
        ProductService service = serviceWithSavingRepository();
        ProductRequest request = request(List.of());

        ProductResponse response = service.createProduct(request, null);

        assertThat(response.hasVariants()).isTrue();
        assertThat(response.variantAttributes())
                .extracting(attribute -> attribute.name())
                .containsExactly("Farbe", "Groesse");
        assertThat(response.variants()).hasSize(4);
        assertThat(response.variants())
                .extracting(ProductResponse::description)
                .containsOnly("Atmungsaktives Team-Shirt");
        assertThat(response.variants())
                .extracting(ProductResponse::sku)
                .containsExactly("TEAM-SHIRT-V1", "TEAM-SHIRT-V2", "TEAM-SHIRT-V3", "TEAM-SHIRT-V4");
        assertThat(response.variants().getFirst().variantValues())
                .containsEntry("Farbe", "Rot")
                .containsEntry("Groesse", "S");
    }

    @Test
    void createProductPersistsPerVariantOverrides() {
        ProductService service = serviceWithSavingRepository();
        ProductVariantRequest customVariant = new ProductVariantRequest(
                null,
                "SHIRT-ROT-S",
                new BigDecimal("34.99"),
                7,
                "https://example.test/rot-s.jpg",
                Map.of("Farbe", "Rot", "Groesse", "S")
        );

        ProductResponse response = service.createProduct(request(List.of(customVariant)), null);

        assertThat(response.variants()).hasSize(1);
        ProductResponse variant = response.variants().getFirst();
        assertThat(variant.sku()).isEqualTo("SHIRT-ROT-S");
        assertThat(variant.recommendedRetailPrice()).isEqualByComparingTo("34.99");
        assertThat(variant.stock()).isEqualTo(7);
        assertThat(variant.imageUrl()).isEqualTo("https://example.test/rot-s.jpg");
        assertThat(variant.description()).isEqualTo(response.description());
    }

    @Test
    void createProductAllowsUnavailableCombinationsToBeOmitted() {
        ProductService service = serviceWithSavingRepository();
        ProductVariantRequest redSmall = variant("SHIRT-ROT-S", "Rot", "S");
        ProductVariantRequest blueMedium = variant("SHIRT-BLAU-M", "Blau", "M");

        ProductResponse response = service.createProduct(request(List.of(redSmall, blueMedium)), null);

        assertThat(response.variants()).hasSize(2);
        assertThat(response.variants())
                .extracting(ProductResponse::sku)
                .containsExactly("SHIRT-ROT-S", "SHIRT-BLAU-M");
        assertThat(response.variants())
                .extracting(ProductResponse::variantValues)
                .containsExactly(
                        Map.of("Farbe", "Rot", "Groesse", "S"),
                        Map.of("Farbe", "Blau", "Groesse", "M")
                );
    }

    private ProductService serviceWithSavingRepository() {
        ProductRepository productRepository = mock(ProductRepository.class);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new ProductService(productRepository, mock(AuditLogService.class));
    }

    private ProductRequest request(List<ProductVariantRequest> variants) {
        return new ProductRequest(
                "Team Shirt",
                "Atmungsaktives Team-Shirt",
                "https://example.test/shirt.jpg",
                new BigDecimal("29.99"),
                new BigDecimal("1.250"),
                ProductEcoScore.B,
                "Textilien",
                20,
                "TEAM-SHIRT",
                true,
                false,
                null,
                true,
                List.of(
                        new ProductVariantAttributeRequest("Farbe", List.of("Rot", "Blau")),
                        new ProductVariantAttributeRequest("Groesse", List.of("S", "M"))
                ),
                variants
        );
    }

    private ProductVariantRequest variant(String sku, String color, String size) {
        return new ProductVariantRequest(
                null,
                sku,
                new BigDecimal("29.99"),
                20,
                "https://example.test/shirt.jpg",
                Map.of("Farbe", color, "Groesse", size)
        );
    }
}
