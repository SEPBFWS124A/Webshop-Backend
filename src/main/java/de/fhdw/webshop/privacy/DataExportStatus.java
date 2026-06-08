package de.fhdw.webshop.privacy;

/**
 * #152 — Lifecycle of a GDPR data-export (subject access) request.
 */
public enum DataExportStatus {
    /** Guest request awaiting double-opt-in confirmation. */
    PENDING_VERIFICATION,
    /** Verified and queued for the background worker. */
    VERIFIED,
    /** Background worker is aggregating the data. */
    PROCESSING,
    /** Archive ready; secure download link is active. */
    READY,
    /** Download window elapsed (7 days / 3 downloads); archive bytes purged. */
    EXPIRED,
    /** Aggregation failed; eligible for SLA escalation. */
    FAILED
}
