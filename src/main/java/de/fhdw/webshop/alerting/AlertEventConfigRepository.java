package de.fhdw.webshop.alerting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertEventConfigRepository extends JpaRepository<AlertEventConfig, Long> {

    Optional<AlertEventConfig> findByEventType(AlertEventType eventType);

    List<AlertEventConfig> findAllByOrderByEventTypeAsc();
}
