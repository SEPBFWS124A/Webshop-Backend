package de.fhdw.webshop.productai;

import de.fhdw.webshop.productai.dto.GenerateContentRequest;
import de.fhdw.webshop.productai.dto.GenerateContentResponse;
import de.fhdw.webshop.productai.dto.SuggestCategoryRequest;
import de.fhdw.webshop.productai.dto.SuggestCategoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/ai")
@RequiredArgsConstructor
public class ProductAIController {

    private final ProductAIService productAIService;

    /** #122 — Generate SEO-optimised product title and description from bullet points. */
    @PostMapping("/generate-content")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<GenerateContentResponse> generateContent(
            @Valid @RequestBody GenerateContentRequest request) {
        return ResponseEntity.ok(productAIService.generateContent(request));
    }

    /** #122 — Suggest category and tags based on product text. */
    @PostMapping("/suggest-category")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<SuggestCategoryResponse> suggestCategory(
            @Valid @RequestBody SuggestCategoryRequest request) {
        return ResponseEntity.ok(productAIService.suggestCategory(request));
    }
}
