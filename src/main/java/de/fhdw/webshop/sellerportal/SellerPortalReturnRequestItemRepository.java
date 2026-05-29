package de.fhdw.webshop.sellerportal;

import de.fhdw.webshop.returnrequest.ReturnRequestItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SellerPortalReturnRequestItemRepository extends JpaRepository<ReturnRequestItem, Long> {

    @EntityGraph(attributePaths = {
            "returnRequest",
            "returnRequest.customer",
            "returnRequest.order",
            "orderItem",
            "orderItem.order",
            "orderItem.product"
    })
    @Query("""
            SELECT item FROM ReturnRequestItem item
            WHERE LOWER(item.orderItem.sellerName) = LOWER(:sellerName)
            ORDER BY item.returnRequest.createdAt DESC, item.id DESC
            """)
    List<ReturnRequestItem> findBySellerName(@Param("sellerName") String sellerName);
}
