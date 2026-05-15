package de.fhdw.webshop.quote;

import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.quote.dto.CreateQuoteRequest;
import de.fhdw.webshop.quote.dto.QuoteRequestResponse;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quote-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class QuoteRequestController {

    private final QuoteRequestService quoteRequestService;

    @PostMapping
    public ResponseEntity<QuoteRequestResponse> createQuoteRequest(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody(required = false) CreateQuoteRequest request) {
        return ResponseEntity.ok(quoteRequestService.createQuoteRequest(currentUser, request));
    }

    @GetMapping
    public ResponseEntity<List<QuoteRequestResponse>> listQuoteRequests(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(quoteRequestService.listQuoteRequests(currentUser));
    }

    @PostMapping("/{id}/restore-cart")
    public ResponseEntity<CartResponse> restoreQuoteToCart(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(quoteRequestService.restoreQuoteToCart(currentUser, id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadQuotePdf(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        QuoteRequest quoteRequest = quoteRequestService.loadOwnedQuote(currentUser, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("angebot-" + quoteRequest.getQuoteNumber() + ".pdf")
                        .build()
                        .toString())
                .body(quoteRequest.getPdfDocument());
    }
}
