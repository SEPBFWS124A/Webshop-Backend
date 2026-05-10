package de.fhdw.webshop.sellerapplication;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerApplicationRepository extends JpaRepository<SellerApplication, Long> {
    java.util.List<SellerApplication> findAllByOrderByCreatedAtDescIdDesc();
}
