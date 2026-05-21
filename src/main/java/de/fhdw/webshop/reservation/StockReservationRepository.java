package de.fhdw.webshop.reservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    Optional<StockReservation> findByCartItemIdAndStatus(Long cartItemId, StockReservationStatus status);

    List<StockReservation> findByUserIdAndStatus(Long userId, StockReservationStatus status);

    List<StockReservation> findByStatusOrderByReservedUntilAsc(StockReservationStatus status);

    List<StockReservation> findByStatusAndReservedUntilBefore(StockReservationStatus status, Instant now);

    @Query("""
            SELECT COALESCE(SUM(r.quantity), 0)
            FROM StockReservation r
            WHERE r.product.id = :productId
              AND r.status = :status
              AND r.reservedUntil > :now
            """)
    int sumActiveQuantityByProductId(
            @Param("productId") Long productId,
            @Param("status") StockReservationStatus status,
            @Param("now") Instant now
    );

    @Query("""
            SELECT COALESCE(SUM(r.quantity), 0)
            FROM StockReservation r
            WHERE r.product.id = :productId
              AND r.status = :status
              AND r.reservedUntil > :now
              AND (:cartItemId IS NULL OR r.cartItemId <> :cartItemId)
            """)
    int sumActiveQuantityByProductIdExcludingCartItem(
            @Param("productId") Long productId,
            @Param("cartItemId") Long cartItemId,
            @Param("status") StockReservationStatus status,
            @Param("now") Instant now
    );
}
