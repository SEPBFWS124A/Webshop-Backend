package de.fhdw.webshop.privacy;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * #152 — GDPR data export. The archive is generated synchronously and returned
 * directly for download in the browser (no email delivery). Registered users
 * confirm with their password; rate limiting (1 / 30 days) still applies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataExportRequestService {

    private static final long RATE_LIMIT_DAYS = 30;

    private final DataExportRequestRepository repository;
    private final DataExportPackageBuilder packageBuilder;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    /** Result returned to the controller for streaming to the browser. */
    public record ExportArchive(String filename, byte[] data) {}

    /** #152 — Registered users: confirm with password, then download immediately. */
    @Transactional
    public ExportArchive generateForRegisteredUser(User user, String password) {
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Passwort ist nicht korrekt.");
        }
        enforceRateLimit(user.getEmail());

        DataExportRequest request = new DataExportRequest();
        request.setUser(user);
        request.setEmail(user.getEmail());
        request.setRequesterType(RequesterType.REGISTERED);
        request.setVerifiedAt(Instant.now());
        return generate(request, user);
    }

    /** #152 — Guests: enter email, then download immediately. */
    @Transactional
    public ExportArchive generateForGuest(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        enforceRateLimit(email);

        DataExportRequest request = new DataExportRequest();
        request.setEmail(email);
        request.setRequesterType(RequesterType.GUEST);
        request.setVerifiedAt(Instant.now());
        return generate(request, null);
    }

    private ExportArchive generate(DataExportRequest request, User auditUser) {
        request.setStatus(DataExportStatus.PROCESSING);
        request.setProcessingStartedAt(Instant.now());
        repository.save(request);
        recordAudit(auditUser, "DATA_EXPORT_REQUESTED", request,
                "Datenauskunft angefordert (" + request.getRequesterType() + ").");

        try {
            byte[] archive = packageBuilder.buildArchive(request);
            request.setStatus(DataExportStatus.READY);
            request.setArchiveSizeBytes((long) archive.length);
            request.setArchiveFilename("datenauskunft.zip");
            request.setCompletedAt(Instant.now());
            repository.save(request);

            recordAudit(auditUser, "DATA_EXPORT_GENERATED", request,
                    "Datenarchiv erstellt (" + archive.length + " Bytes).");
            recordAudit(auditUser, "DATA_EXPORT_DOWNLOADED", request,
                    "Datenarchiv direkt im Browser heruntergeladen.");
            log.info("Data export {} generated and delivered ({} bytes)", request.getId(), archive.length);
            return new ExportArchive(request.getArchiveFilename(), archive);
        } catch (Exception e) {
            // #154 — Resilient fallback: deliver a mock/basic package instead of failing with 500.
            log.error("Data export {} aggregation failed, delivering fallback package: {}",
                    request.getId(), e.getMessage(), e);
            try {
                byte[] fallback = packageBuilder.buildFallbackArchive(request, e.getMessage());
                request.setStatus(DataExportStatus.READY);
                request.setArchiveSizeBytes((long) fallback.length);
                request.setArchiveFilename("datenauskunft.zip");
                request.setErrorMessage("Fallback (Mockdaten): " + e.getMessage());
                request.setCompletedAt(Instant.now());
                repository.save(request);
                recordAudit(auditUser, "DATA_EXPORT_FALLBACK", request,
                        "Aggregierung fehlgeschlagen – Mock-/Fallback-Paket ausgeliefert: " + e.getMessage());
                return new ExportArchive(request.getArchiveFilename(), fallback);
            } catch (Exception fallbackError) {
                log.error("Data export {} fallback also failed: {}", request.getId(),
                        fallbackError.getMessage(), fallbackError);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Die Datenauskunft konnte nicht erstellt werden. Bitte versuche es später erneut.");
            }
        }
    }

    private void recordAudit(User user, String action, DataExportRequest request, String details) {
        if (user != null) {
            auditLogService.record(user, action, "DataExportRequest", request.getId(), AuditInitiator.USER, details);
        } else {
            auditLogService.recordSystemAction(action, "DataExportRequest", request.getId(), details);
        }
    }

    private void enforceRateLimit(String email) {
        Optional<DataExportRequest> last = repository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email);
        if (last.isPresent()) {
            DataExportRequest r = last.get();
            boolean recent = r.getCreatedAt().isAfter(Instant.now().minus(RATE_LIMIT_DAYS, ChronoUnit.DAYS));
            boolean active = r.getStatus() != DataExportStatus.FAILED;
            if (recent && active) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Für diese E-Mail-Adresse wurde innerhalb der letzten 30 Tage bereits eine Datenauskunft angefordert.");
            }
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte gib eine gültige E-Mail-Adresse an.");
        }
        return email.trim().toLowerCase();
    }
}
