package de.fhdw.webshop.warehouse;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseTruckRepository extends JpaRepository<WarehouseTruck, Long> {

    Optional<WarehouseTruck> findByTruckIdentifier(String truckIdentifier);

    List<WarehouseTruck> findByCurrentWarehouseLocationIdOrderByTruckIdentifierAsc(Long warehouseLocationId);

    List<WarehouseTruck> findByOriginWarehouseLocationIdOrderByTruckIdentifierAsc(Long warehouseLocationId);

    List<WarehouseTruck> findByCurrentWarehouseLocationIdAndStatusInOrderByTruckIdentifierAsc(
            Long warehouseLocationId,
            List<TruckStatus> statuses
    );

    List<WarehouseTruck> findByStatusInOrderByTruckIdentifierAsc(List<TruckStatus> statuses);

    @Query("""
            SELECT DISTINCT truck FROM WarehouseTruck truck
            WHERE (:warehouseLocationId IS NULL
                   OR truck.originWarehouseLocation.id = :warehouseLocationId
                   OR truck.currentWarehouseLocation.id = :warehouseLocationId)
            ORDER BY truck.truckIdentifier ASC
            """)
    List<WarehouseTruck> findFleetByWarehouseLocation(@Param("warehouseLocationId") Long warehouseLocationId);
}

