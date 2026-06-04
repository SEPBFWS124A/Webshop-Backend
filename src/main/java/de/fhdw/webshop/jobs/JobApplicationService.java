package de.fhdw.webshop.jobs;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.jobs.dto.*;
import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private static final int MAX_FILES_PER_APPLICATION = 5;
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/jpeg",
            "image/png"
    );

    private static final String DEFAULT_ACCEPTANCE_TEXT =
            "Sehr geehrte/r [VORNAME NACHNAME],\n\n" +
            "vielen Dank für Ihre Bewerbung und Ihr Interesse an unserem Unternehmen.\n\n" +
            "Wir freuen uns, Ihnen mitteilen zu können, dass wir Ihre Unterlagen sehr positiv bewertet haben " +
            "und Sie herzlich zu einem persönlichen Vorstellungsgespräch einladen möchten.\n\n" +
            "Termin: [DATUM], [UHRZEIT] Uhr\n" +
            "Adresse: [STRAßE UND HAUSNUMMER, PLZ ORT]\n\n" +
            "Bitte bestätigen Sie kurz, ob dieser Termin für Sie passt, oder melden Sie sich unter " +
            "[TELEFONNUMMER / E-MAIL-ADRESSE], falls Sie einen anderen Zeitraum bevorzugen.\n\n" +
            "Wir freuen uns auf das Gespräch!\n\n" +
            "Mit freundlichen Grüßen\n[IHR NAME]\nRecruiting-Team";

    private static final String DEFAULT_REJECTION_TEXT =
            "Sehr geehrte/r [VORNAME NACHNAME],\n\n" +
            "vielen Dank für Ihre Bewerbung auf die Stelle als [STELLENBEZEICHNUNG] sowie für das " +
            "Interesse an unserem Unternehmen.\n\n" +
            "Nach sorgfältiger Prüfung aller eingegangenen Bewerbungen müssen wir Ihnen leider mitteilen, " +
            "dass wir uns für eine/n andere/n Kandidaten/in entschieden haben. " +
            "Diese Entscheidung ist uns nicht leichtgefallen, da wir viele qualifizierte Bewerbungen erhalten haben.\n\n" +
            "Wir wünschen Ihnen für Ihren weiteren Berufsweg alles Gute und viel Erfolg.\n\n" +
            "Mit freundlichen Grüßen\n[IHR NAME]\nRecruiting-Team";

    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository applicationRepository;
    private final JobApplicationFileRepository fileRepository;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    public String getDefaultAcceptanceText() {
        return DEFAULT_ACCEPTANCE_TEXT;
    }

    public String getDefaultRejectionText() {
        return DEFAULT_REJECTION_TEXT;
    }

    @Transactional
    public JobApplicationResponse submitApplication(Long jobPostingId,
                                                     JobApplicationRequest request,
                                                     List<MultipartFile> files) {
        JobPosting posting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new EntityNotFoundException("Stellenanzeige nicht gefunden: " + jobPostingId));
        if (!"ACTIVE".equals(posting.getStatus())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Diese Stellenanzeige ist nicht mehr aktiv.");
        }
        validateApplication(request, files);

        JobApplication application = new JobApplication();
        application.setJobPosting(posting);
        application.setApplicantName(request.applicantName().trim());
        application.setApplicantEmail(request.applicantEmail().trim());
        application.setApplicantPhone(request.applicantPhone() != null ? request.applicantPhone().trim() : null);
        application.setMotivationText(request.motivationText());
        application.setStatus("OPEN");

        JobApplication saved = applicationRepository.save(application);
        saveFiles(saved, files);
        return toDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<JobApplicationSummaryResponse> getApplications(Long jobPostingId, String status) {
        Long postingId = (jobPostingId != null && jobPostingId > 0) ? jobPostingId : null;
        String st = (status != null && !status.isBlank()) ? status.toUpperCase() : null;
        List<JobApplication> applications;

        if (postingId != null && st != null) {
            applications = applicationRepository.findByJobPostingIdAndStatusOrderByCreatedAtDesc(postingId, st);
        } else if (postingId != null) {
            applications = applicationRepository.findByJobPostingIdOrderByCreatedAtDesc(postingId);
        } else if (st != null) {
            applications = applicationRepository.findByStatusOrderByCreatedAtDesc(st);
        } else {
            applications = applicationRepository.findAllByOrderByCreatedAtDesc();
        }

        return applications.stream().map(this::toSummaryResponse).toList();
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse getApplication(Long id) {
        JobApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bewerbung nicht gefunden: " + id));
        return toDetailResponse(application);
    }

    @Transactional
    public JobApplicationResponse acceptApplication(Long id, String customEmailText, User admin) {
        JobApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bewerbung nicht gefunden: " + id));
        if (!"OPEN".equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bewerbung wurde bereits bearbeitet.");
        }
        application.setStatus("ACCEPTED");
        JobApplication saved = applicationRepository.save(application);

        String subject = "Ihre Bewerbung bei uns – Positive Rückmeldung";
        String body = customEmailText != null && !customEmailText.isBlank() ? customEmailText : DEFAULT_ACCEPTANCE_TEXT;
        emailService.sendEmail(saved.getApplicantEmail(), subject, body);

        auditLogService.record(admin, "ACCEPT_JOB_APPLICATION", "JobApplication", saved.getId(),
                AuditInitiator.ADMIN,
                "Bewerbung von " + saved.getApplicantName() + " für Stelle \"" +
                        saved.getJobPosting().getTitle() + "\" angenommen");
        return toDetailResponse(saved);
    }

    @Transactional
    public JobApplicationResponse rejectApplication(Long id, String customEmailText, User admin) {
        JobApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bewerbung nicht gefunden: " + id));
        if (!"OPEN".equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bewerbung wurde bereits bearbeitet.");
        }
        application.setStatus("REJECTED");
        JobApplication saved = applicationRepository.save(application);

        String subject = "Ihre Bewerbung bei uns – Rückmeldung";
        String body = customEmailText != null && !customEmailText.isBlank() ? customEmailText : DEFAULT_REJECTION_TEXT;
        emailService.sendEmail(saved.getApplicantEmail(), subject, body);

        auditLogService.record(admin, "REJECT_JOB_APPLICATION", "JobApplication", saved.getId(),
                AuditInitiator.ADMIN,
                "Bewerbung von " + saved.getApplicantName() + " für Stelle \"" +
                        saved.getJobPosting().getTitle() + "\" abgelehnt");
        return toDetailResponse(saved);
    }

    private void validateApplication(JobApplicationRequest request, List<MultipartFile> files) {
        if (request.applicantName() == null || request.applicantName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name darf nicht leer sein.");
        }
        if (request.applicantEmail() == null || request.applicantEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-Mail-Adresse darf nicht leer sein.");
        }
        if (files == null || files.isEmpty()) return;
        if (files.size() > MAX_FILES_PER_APPLICATION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Es können maximal " + MAX_FILES_PER_APPLICATION + " Dateien hochgeladen werden.");
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leere Dateien sind nicht erlaubt.");
            }
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Eine Datei darf maximal 5 MB groß sein.");
            }
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType.toLowerCase())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Nur PDF, Word-Dokumente und Bilder (JPG, PNG) sind erlaubt.");
            }
        }
    }

    private void saveFiles(JobApplication application, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;
        for (int i = 0; i < files.size(); i++) {
            MultipartFile upload = files.get(i);
            JobApplicationFile file = new JobApplicationFile();
            file.setApplication(application);
            file.setOriginalFilename(upload.getOriginalFilename() != null ? upload.getOriginalFilename() : "datei");
            String contentType = upload.getContentType();
            file.setContentType(contentType != null ? contentType.toLowerCase() : "application/octet-stream");
            file.setFileSizeBytes(upload.getSize());
            file.setFileType(resolveFileType(file.getOriginalFilename(), i));
            try {
                file.setFileData(upload.getBytes());
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Datei konnte nicht gelesen werden.");
            }
            fileRepository.save(file);
        }
    }

    private String resolveFileType(String filename, int index) {
        if (filename == null) return "OTHER";
        String lower = filename.toLowerCase();
        if (lower.contains("anschreiben") || lower.contains("cover")) return "COVER_LETTER";
        if (lower.contains("lebenslauf") || lower.contains("cv") || lower.contains("resume")) return "RESUME";
        if (index == 0) return "COVER_LETTER";
        if (index == 1) return "RESUME";
        return "OTHER";
    }

    private JobApplicationSummaryResponse toSummaryResponse(JobApplication app) {
        return new JobApplicationSummaryResponse(
                app.getId(),
                app.getJobPosting().getId(),
                app.getJobPosting().getTitle(),
                app.getApplicantName(),
                app.getApplicantEmail(),
                app.getStatus(),
                app.getCreatedAt());
    }

    private JobApplicationResponse toDetailResponse(JobApplication app) {
        List<JobApplicationFileResponse> files = fileRepository
                .findByApplicationIdOrderByCreatedAtAsc(app.getId())
                .stream().map(this::toFileResponse).toList();
        return new JobApplicationResponse(
                app.getId(),
                app.getJobPosting().getId(),
                app.getJobPosting().getTitle(),
                app.getApplicantName(),
                app.getApplicantEmail(),
                app.getApplicantPhone(),
                app.getMotivationText(),
                app.getStatus(),
                app.getCreatedAt(),
                app.getUpdatedAt(),
                files);
    }

    private JobApplicationFileResponse toFileResponse(JobApplicationFile file) {
        String base64 = Base64.getEncoder().encodeToString(file.getFileData());
        return new JobApplicationFileResponse(
                file.getId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getFileSizeBytes(),
                file.getFileType(),
                base64,
                file.getCreatedAt());
    }
}
