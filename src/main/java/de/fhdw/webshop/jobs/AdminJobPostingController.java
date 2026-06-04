package de.fhdw.webshop.jobs;

import de.fhdw.webshop.jobs.dto.CreateJobPostingRequest;
import de.fhdw.webshop.jobs.dto.JobPostingResponse;
import de.fhdw.webshop.jobs.dto.UpdateJobPostingStatusRequest;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminJobPostingController {

    private final JobPostingService jobPostingService;

    @GetMapping("/api/admin/jobs")
    public ResponseEntity<List<JobPostingResponse>> getPostings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String employmentType,
            @RequestParam(required = false) String location) {
        return ResponseEntity.ok(jobPostingService.getAdminPostings(status, employmentType, location));
    }

    @PostMapping("/api/admin/jobs")
    public ResponseEntity<JobPostingResponse> createPosting(
            @Valid @RequestBody CreateJobPostingRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobPostingService.createPosting(request, currentUser));
    }

    @PutMapping("/api/admin/jobs/{id}")
    public ResponseEntity<JobPostingResponse> updatePosting(
            @PathVariable Long id,
            @Valid @RequestBody CreateJobPostingRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(jobPostingService.updatePosting(id, request, currentUser));
    }

    @PutMapping("/api/admin/jobs/{id}/status")
    public ResponseEntity<JobPostingResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateJobPostingStatusRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(jobPostingService.updateStatus(id, request.status(), currentUser));
    }

    @DeleteMapping("/api/admin/jobs/{id}")
    public ResponseEntity<Void> deletePosting(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        jobPostingService.deletePosting(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
