package de.fhdw.webshop.admin;

import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Records significant actions in the audit log.
 * Call record() from any service layer to capture user- or system-initiated events.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void record(User user, String action, String entityType, Long entityId,
                       AuditInitiator initiatedBy, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUser(user);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setInitiatedBy(initiatedBy);
        auditLog.setDetails(details);
        auditLogRepository.save(auditLog);
    }

    public void recordSystemAction(String action, String entityType, Long entityId, String details) {
        record(null, action, entityType, entityId, AuditInitiator.SYSTEM, details);
    }
}
