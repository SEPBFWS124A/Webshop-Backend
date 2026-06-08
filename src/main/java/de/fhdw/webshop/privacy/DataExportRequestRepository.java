package de.fhdw.webshop.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DataExportRequestRepository extends JpaRepository<DataExportRequest, Long> {

    Optional<DataExportRequest> findByVerificationToken(String verificationToken);

    Optional<DataExportRequest> findByDownloadToken(String downloadToken);

    List<DataExportRequest> findByStatus(DataExportStatus status);

    /** Rate limiting: most recent request for an email address. */
    Optional<DataExportRequest> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    /** Worker: ready archives whose download window has elapsed. */
    List<DataExportRequest> findByStatusAndDownloadExpiresAtBefore(DataExportStatus status, Instant cutoff);

    /** SLA: open requests older than the escalation threshold and not yet escalated. */
    List<DataExportRequest> findByStatusInAndEscalatedFalseAndCreatedAtBefore(
            List<DataExportStatus> statuses, Instant cutoff);
}
