package de.fhdw.webshop.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Flexible product search supporting all customer-facing filter combinations (US #46, #47).
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
            WHERE (:purchasableOnly IS NULL OR p.purchasable = :purchasableOnly)
              AND (:category = '' OR LOWER(p.category) = LOWER(:category))
              AND (:searchTerm = ''
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            ORDER BY p.promoted DESC, p.name ASC
            """)
    List<Product> searchProducts(
            @Param("purchasableOnly") Boolean purchasableOnly,
            @Param("category") String category,
            @Param("searchTerm") String searchTerm
    );
}
