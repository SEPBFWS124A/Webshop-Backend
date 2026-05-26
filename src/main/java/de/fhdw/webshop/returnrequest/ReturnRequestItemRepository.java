package de.fhdw.webshop.returnrequest;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReturnRequestItemRepository extends JpaRepository<ReturnRequestItem, Long> {

    boolean existsByOrderItemId(Long orderItemId);

    @Query("""
            select coalesce(sum(item.quantity), 0)
            from ReturnRequestItem item
            where item.orderItem.id = :orderItemId
              and item.returnRequest.status not in :excludedStatuses
            """)
    Integer sumReturnedQuantityForOrderItem(
            @Param("orderItemId") Long orderItemId,
            @Param("excludedStatuses") Collection<ReturnRequestStatus> excludedStatuses);
}
