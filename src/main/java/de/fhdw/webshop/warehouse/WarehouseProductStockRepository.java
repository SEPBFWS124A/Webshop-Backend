package de.fhdw.webshop.warehouse;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseProductStockRepository extends JpaRepository<WarehouseProductStock, Long> {

    Optional<WarehouseProductStock> findByProductIdAndWarehouseLocationId(Long productId, Long warehouseLocationId);

    List<WarehouseProductStock> findByWarehouseLocationIdAndProductIdIn(
            Long warehouseLocationId,
            Collection<Long> productIds
    );
}
