package de.fhdw.webshop.alerting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnownEmailAddressRepository extends JpaRepository<KnownEmailAddress, Long> {

    List<KnownEmailAddress> findAllByOrderByLabelAsc();

    boolean existsByEmail(String email);
}
