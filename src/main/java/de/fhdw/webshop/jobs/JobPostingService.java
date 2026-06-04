package de.fhdw.webshop.jobs;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.jobs.dto.CreateJobPostingRequest;
import de.fhdw.webshop.jobs.dto.JobPostingResponse;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JobPostingService {

    private static final Set<String> VALID_EMPLOYMENT_TYPES = Set.of("VOLLZEIT", "TEILZEIT", "MINIJOB");
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "INACTIVE", "ARCHIVED");

    private final JobPostingRepository jobPostingRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<JobPostingResponse> getPublicPostings(String employmentType, String location) {
        String type = (employmentType != null && !employmentType.isBlank()) ? employmentType.toUpperCase() : null;
        String loc  = (location != null && !location.isBlank()) ? location : null;
        List<JobPosting> postings;

        if (type != null && loc != null) {
            postings = jobPostingRepository.findByStatusAndEmploymentTypeAndLocationOrderByDisplayOrderAsc("ACTIVE", type, loc);
        } else if (type != null) {
            postings = jobPostingRepository.findByStatusAndEmploymentTypeOrderByDisplayOrderAsc("ACTIVE", type);
        } else if (loc != null) {
            postings = jobPostingRepository.findByStatusAndLocationOrderByDisplayOrderAsc("ACTIVE", loc);
        } else {
            postings = jobPostingRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE");
        }

        return postings.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public JobPostingResponse getPublicPosting(Long id) {
        JobPosting posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Stellenanzeige nicht gefunden: " + id));
        if (!"ACTIVE".equals(posting.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stellenanzeige nicht gefunden: " + id);
        }
        return toResponse(posting);
    }

    @Transactional(readOnly = true)
    public List<JobPostingResponse> getAdminPostings(String status, String employmentType, String location) {
        String st   = (status != null && !status.isBlank()) ? status.toUpperCase() : null;
        String type = (employmentType != null && !employmentType.isBlank()) ? employmentType.toUpperCase() : null;
        String loc  = (location != null && !location.isBlank()) ? location : null;
        List<JobPosting> postings;

        if (st != null && type != null && loc != null) {
            postings = jobPostingRepository.findByStatusAndEmploymentTypeAndLocationOrderByDisplayOrderAsc(st, type, loc);
        } else if (st != null && type != null) {
            postings = jobPostingRepository.findByStatusAndEmploymentTypeOrderByDisplayOrderAsc(st, type);
        } else if (st != null && loc != null) {
            postings = jobPostingRepository.findByStatusAndLocationOrderByDisplayOrderAsc(st, loc);
        } else if (st != null) {
            postings = jobPostingRepository.findByStatusOrderByDisplayOrderAsc(st);
        } else {
            postings = jobPostingRepository.findAllByOrderByDisplayOrderAsc();
        }

        return postings.stream().map(this::toResponse).toList();
    }

    @Transactional
    public JobPostingResponse createPosting(CreateJobPostingRequest request, User admin) {
        validateRequest(request);
        JobPosting posting = new JobPosting();
        applyRequest(posting, request);
        JobPosting saved = jobPostingRepository.save(posting);
        auditLogService.record(admin, "CREATE_JOB_POSTING", "JobPosting", saved.getId(),
                AuditInitiator.ADMIN, "Stellenanzeige erstellt: " + saved.getTitle());
        return toResponse(saved);
    }

    @Transactional
    public JobPostingResponse updatePosting(Long id, CreateJobPostingRequest request, User admin) {
        JobPosting posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Stellenanzeige nicht gefunden: " + id));
        validateRequest(request);
        applyRequest(posting, request);
        JobPosting saved = jobPostingRepository.save(posting);
        auditLogService.record(admin, "UPDATE_JOB_POSTING", "JobPosting", saved.getId(),
                AuditInitiator.ADMIN, "Stellenanzeige aktualisiert: " + saved.getTitle());
        return toResponse(saved);
    }

    @Transactional
    public JobPostingResponse updateStatus(Long id, String status, User admin) {
        String normalized = status != null ? status.toUpperCase() : "";
        if (!VALID_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ungültiger Status. Erlaubt: ACTIVE, INACTIVE, ARCHIVED");
        }
        JobPosting posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Stellenanzeige nicht gefunden: " + id));
        posting.setStatus(normalized);
        JobPosting saved = jobPostingRepository.save(posting);
        auditLogService.record(admin, "UPDATE_JOB_POSTING_STATUS", "JobPosting", saved.getId(),
                AuditInitiator.ADMIN, "Status geändert zu " + normalized + ": " + saved.getTitle());
        return toResponse(saved);
    }

    @Transactional
    public void deletePosting(Long id, User admin) {
        JobPosting posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Stellenanzeige nicht gefunden: " + id));
        String title = posting.getTitle();
        jobPostingRepository.delete(posting);
        auditLogService.record(admin, "DELETE_JOB_POSTING", "JobPosting", id,
                AuditInitiator.ADMIN, "Stellenanzeige gelöscht: " + title);
    }

    private void validateRequest(CreateJobPostingRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Titel darf nicht leer sein.");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Beschreibung darf nicht leer sein.");
        }
        if (request.employmentType() == null || !VALID_EMPLOYMENT_TYPES.contains(request.employmentType().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ungültiger Beschäftigungstyp. Erlaubt: VOLLZEIT, TEILZEIT, MINIJOB");
        }
        if (request.location() == null || request.location().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Standort darf nicht leer sein.");
        }
    }

    private void applyRequest(JobPosting posting, CreateJobPostingRequest request) {
        posting.setTitle(request.title().trim());
        posting.setDescription(request.description().trim());
        posting.setEmploymentType(request.employmentType().toUpperCase());
        posting.setLocation(request.location().trim());
        posting.setDisplayOrder(request.displayOrder());
        String status = request.status() != null && VALID_STATUSES.contains(request.status().toUpperCase())
                ? request.status().toUpperCase() : "ACTIVE";
        posting.setStatus(status);
    }

    JobPostingResponse toResponse(JobPosting posting) {
        return new JobPostingResponse(
                posting.getId(),
                posting.getTitle(),
                posting.getDescription(),
                posting.getEmploymentType(),
                posting.getLocation(),
                posting.getStatus(),
                posting.getDisplayOrder(),
                posting.getCreatedAt(),
                posting.getUpdatedAt());
    }
}
