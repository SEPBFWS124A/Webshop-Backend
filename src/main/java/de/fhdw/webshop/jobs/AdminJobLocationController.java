package de.fhdw.webshop.jobs;

import de.fhdw.webshop.jobs.dto.CreateJobLocationRequest;
import de.fhdw.webshop.jobs.dto.JobLocationResponse;
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
public class AdminJobLocationController {

    private final JobLocationService jobLocationService;

    @GetMapping("/api/admin/job-locations")
    public ResponseEntity<List<JobLocationResponse>> getAll() {
        return ResponseEntity.ok(jobLocationService.getAll());
    }

    @PostMapping("/api/admin/job-locations")
    public ResponseEntity<JobLocationResponse> create(
            @Valid @RequestBody CreateJobLocationRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobLocationService.create(request, currentUser));
    }

    @PutMapping("/api/admin/job-locations/{id}")
    public ResponseEntity<JobLocationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateJobLocationRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(jobLocationService.update(id, request, currentUser));
    }

    @DeleteMapping("/api/admin/job-locations/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        jobLocationService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
