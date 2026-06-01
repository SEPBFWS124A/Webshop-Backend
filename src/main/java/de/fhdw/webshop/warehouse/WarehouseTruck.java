package de.fhdw.webshop.warehouse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "warehouse_trucks")
@Getter
@Setter
@NoArgsConstructor
public class WarehouseTruck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "truck_identifier", nullable = false, unique = true, length = 50)
    private String truckIdentifier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_warehouse_location_id")
    private WarehouseLocation originWarehouseLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_warehouse_location_id")
    private WarehouseLocation currentWarehouseLocation;

    @Column(name = "driver_id", length = 100)
    private String driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TruckStatus status = TruckStatus.AVAILABLE;

    @Column(name = "departure_time")
    private Instant departureTime;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "route_optimization_id", length = 100)
    private String routeOptimizationId;

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "capacity_orders", nullable = false)
    private int capacityOrders = 10;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}

