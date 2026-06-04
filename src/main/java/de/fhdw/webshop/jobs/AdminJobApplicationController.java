package de.fhdw.webshop.jobs;

import de.fhdw.webshop.jobs.dto.JobApplicationResponse;
import de.fhdw.webshop.jobs.dto.JobApplicationSummaryResponse;
import de.fhdw.webshop.jobs.dto.SendDecisionEmailRequest;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminJobApplicationController {

    private final JobApplicationService jobApplicationService;

    @GetMapping("/api/admin/job-applications")
    public ResponseEntity<List<JobApplicationSummaryResponse>> getApplications(
            @RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(jobApplicationService.getApplications(jobPostingId, status));
    }

    @GetMapping("/api/admin/job-applications/{id}")
    public ResponseEntity<JobApplicationResponse> getApplication(@PathVariable Long id) {
        return ResponseEntity.ok(jobApplicationService.getApplication(id));
    }

    @GetMapping("/api/admin/job-applications/default-acceptance-text")
    public ResponseEntity<String> getDefaultAcceptanceText() {
        return ResponseEntity.ok(jobApplicationService.getDefaultAcceptanceText());
    }

    @GetMapping("/api/admin/job-applications/default-rejection-text")
    public ResponseEntity<String> getDefaultRejectionText() {
        return ResponseEntity.ok(jobApplicationService.getDefaultRejectionText());
    }

    @PostMapping("/api/admin/job-applications/{id}/accept")
    public ResponseEntity<JobApplicationResponse> accept(
            @PathVariable Long id,
            @Valid @RequestBody SendDecisionEmailRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(jobApplicationService.acceptApplication(id, request.customEmailText(), currentUser));
    }

    @PostMapping("/api/admin/job-applications/{id}/reject")
    public ResponseEntity<JobApplicationResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody SendDecisionEmailRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(jobApplicationService.rejectApplication(id, request.customEmailText(), currentUser));
    }
}
