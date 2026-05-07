package de.fhdw.webshop.warehouse;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WarehouseLocationRepository extends JpaRepository<WarehouseLocation, Long> {

    @Query("""
            SELECT location FROM WarehouseLocation location
            WHERE location.active = true
            ORDER BY location.mainLocation DESC, location.name ASC
            """)
    List<WarehouseLocation> findActiveLocations();

    Optional<WarehouseLocation> findFirstByMainLocationTrueAndActiveTrueOrderByIdAsc();
}
