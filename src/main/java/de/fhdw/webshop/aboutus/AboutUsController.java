package de.fhdw.webshop.aboutus;

import de.fhdw.webshop.aboutus.dto.AboutUsSectionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AboutUsController {

    private final AboutUsService aboutUsService;

    @GetMapping("/api/about-us/sections")
    public ResponseEntity<List<AboutUsSectionResponse>> getSections() {
        return ResponseEntity.ok(aboutUsService.getAllSections());
    }
}
