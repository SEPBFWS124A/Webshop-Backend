package de.fhdw.webshop.agb;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgbVersionRepository extends JpaRepository<AgbVersion, Long> {
    Optional<AgbVersion> findTopByOrderByCreatedAtDesc();
}
