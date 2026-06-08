package de.fhdw.webshop.privacy;

import de.fhdw.webshop.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "data_export_requests")
@Getter
@Setter
@NoArgsConstructor
public class DataExportRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for guest requests. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DataExportStatus status = DataExportStatus.PENDING_VERIFICATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "requester_type", nullable = false, length = 20)
    private RequesterType requesterType = RequesterType.GUEST;

    @Column(name = "verification_token", length = 80)
    private String verificationToken;

    @Column(name = "verification_expires_at")
    private Instant verificationExpiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "download_token", length = 80)
    private String downloadToken;

    @Column(name = "download_expires_at")
    private Instant downloadExpiresAt;

    @Column(name = "download_count", nullable = false)
    private int downloadCount = 0;

    @Column(name = "max_downloads", nullable = false)
    private int maxDownloads = 3;

    // No @Lob: on PostgreSQL @Lob maps byte[] to OID, but the column is BYTEA.
    @Column(name = "archive_data")
    private byte[] archiveData;

    @Column(name = "archive_filename", length = 160)
    private String archiveFilename;

    @Column(name = "archive_size_bytes")
    private Long archiveSizeBytes;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private boolean escalated = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
