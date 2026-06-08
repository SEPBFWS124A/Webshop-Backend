package de.fhdw.webshop.privacy;

import de.fhdw.webshop.privacy.dto.GuestExportRequest;
import de.fhdw.webshop.privacy.dto.RegisteredExportRequest;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/privacy/data-requests")
@RequiredArgsConstructor
public class DataExportController {

    private final DataExportRequestService service;

    /** #152 — Registered user: confirm with password and download the archive directly. */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ByteArrayResource> requestAsRegistered(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RegisteredExportRequest body) {
        return toDownload(service.generateForRegisteredUser(user, body.password()));
    }

    /** #152 — Guest: enter email and download the archive directly (no email delivery). */
    @PostMapping("/guest")
    public ResponseEntity<ByteArrayResource> requestAsGuest(@Valid @RequestBody GuestExportRequest body) {
        return toDownload(service.generateForGuest(body.email()));
    }

    private ResponseEntity<ByteArrayResource> toDownload(DataExportRequestService.ExportArchive archive) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archive.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new ByteArrayResource(archive.data()));
    }
}
