package de.fhdw.webshop.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Arrays;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    default List<Product> searchProducts(Boolean purchasableOnly, String category, String searchTerm) {
        return searchProducts(
                purchasableOnly,
                category,
                false,
                Arrays.asList(ProductEcoScore.values()),
                searchTerm
        );
    }

    /**
     * Flexible product search supporting all customer-facing filter combinations (US #46, #47, #198).
     * Passing null for any parameter disables that filter.
     */
    /**
     * Pass empty string ("") for category and searchTerm to skip those filters.
     * purchasableOnly = null means "no filter on purchasable".
     * We avoid IS NULL on String parameters because PostgreSQL infers null Strings
     * as bytea, which breaks LOWER().
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.parentProduct IS NULL
              AND (:purchasableOnly IS NULL OR p.purchasable = :purchasableOnly)
              AND (:category = '' OR LOWER(p.category) = LOWER(:category))
              AND (:filterByEcoScore = false OR p.ecoScore IN :ecoScores)
              AND (:searchTerm = ''
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            ORDER BY p.promoted DESC, p.name ASC
            """)
    List<Product> searchProducts(
            @Param("purchasableOnly") Boolean purchasableOnly,
            @Param("category") String category,
            @Param("filterByEcoScore") boolean filterByEcoScore,
            @Param("ecoScores") List<ProductEcoScore> ecoScores,
            @Param("searchTerm") String searchTerm
    );
}
