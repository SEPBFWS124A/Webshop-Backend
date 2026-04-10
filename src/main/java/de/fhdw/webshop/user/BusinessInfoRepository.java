package de.fhdw.webshop.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessInfoRepository extends JpaRepository<BusinessInfo, Long> {
    Optional<BusinessInfo> findByUserId(Long userId);
}
