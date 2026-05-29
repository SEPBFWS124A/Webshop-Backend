package de.fhdw.webshop.sellerportal;

import de.fhdw.webshop.order.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerPortalOrderItemRepository extends JpaRepository<OrderItem, Long> {

    @EntityGraph(attributePaths = {"order", "order.customer", "product"})
    List<OrderItem> findBySellerNameIgnoreCaseOrderByOrderCreatedAtDesc(String sellerName);
}
