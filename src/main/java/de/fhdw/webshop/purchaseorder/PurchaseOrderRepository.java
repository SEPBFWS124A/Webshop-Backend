package de.fhdw.webshop.purchaseorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    List<PurchaseOrder> findAllByOrderByOrderedAtDesc();

    List<PurchaseOrder> findByProductIdOrderByOrderedAtDesc(Long productId);
}
