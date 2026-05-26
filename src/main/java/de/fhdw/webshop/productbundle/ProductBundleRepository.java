package de.fhdw.webshop.productbundle;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductBundleRepository extends JpaRepository<ProductBundle, Long> {

    @Override
    @EntityGraph(attributePaths = {"items", "items.product"})
    List<ProductBundle> findAll();

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<ProductBundle> findByActiveTrueOrderByFeaturedDescCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<ProductBundle> findDistinctByActiveTrueAndItemsProductIdOrderByFeaturedDescCreatedAtDescIdDesc(Long productId);

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<ProductBundle> findById(Long id);
}
