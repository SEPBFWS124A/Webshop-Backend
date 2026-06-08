package de.fhdw.webshop.aboutus;

import de.fhdw.webshop.aboutus.dto.*;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAboutUsController {

    private final AboutUsService aboutUsService;

    @GetMapping("/api/admin/about-us/sections")
    public ResponseEntity<List<AboutUsSectionResponse>> getAllSections() {
        return ResponseEntity.ok(aboutUsService.getAllSections());
    }

    @PostMapping(value = "/api/admin/about-us/sections", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AboutUsSectionResponse> createSection(
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(defaultValue = "0") int displayOrder,
            @RequestParam(defaultValue = "TEXT_ONLY") String layoutType,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal User currentUser) {
        CreateAboutUsSectionRequest request = new CreateAboutUsSectionRequest(title, content, displayOrder, layoutType);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aboutUsService.createSection(request, images, currentUser));
    }

    @PutMapping(value = "/api/admin/about-us/sections/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AboutUsSectionResponse> updateSection(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(defaultValue = "0") int displayOrder,
            @RequestParam(defaultValue = "TEXT_ONLY") String layoutType,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal User currentUser) {
        CreateAboutUsSectionRequest request = new CreateAboutUsSectionRequest(title, content, displayOrder, layoutType);
        return ResponseEntity.ok(aboutUsService.updateSection(id, request, images, currentUser));
    }

    @DeleteMapping("/api/admin/about-us/sections/{id}")
    public ResponseEntity<Void> deleteSection(@PathVariable Long id,
                                               @AuthenticationPrincipal User currentUser) {
        aboutUsService.deleteSection(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/about-us/sections/reorder")
    public ResponseEntity<List<AboutUsSectionResponse>> reorderSections(
            @Valid @RequestBody ReorderAboutUsSectionsRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(aboutUsService.reorderSections(request.orderedIds(), currentUser));
    }

    @DeleteMapping("/api/admin/about-us/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long imageId,
                                             @AuthenticationPrincipal User currentUser) {
        aboutUsService.deleteImage(imageId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
