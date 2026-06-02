package de.fhdw.webshop.product;

import de.fhdw.webshop.product.dto.ProductFeedbackDto;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product-feedback")
public class ProductFeedbackController {

    private final ProductFeedbackRepository repository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProductFeedbackController(
            ProductFeedbackRepository repository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<ProductFeedbackDto> getAllFeedback() {
        return repository.findAll().stream().map(feedback -> {
            String title = productRepository.findById(feedback.getProductId())
                    .map(Product::getName)
                    .orElse("Unbekanntes Produkt");

            String name = userRepository.findById(feedback.getUserId())
                    .map(User::getUsername)
                    .orElse("Gast");

            return new ProductFeedbackDto(
                    feedback.getId(),
                    title,
                    name,
                    feedback.getRating(),
                    feedback.getComment(),
                    feedback.getCreatedAt(),
                    feedback.getSource()
            );
        }).toList();
    }

    @PostMapping
    public ProductFeedback createFeedback(@RequestBody ProductFeedback feedback) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof User user) {
            feedback.setUserId(user.getId());
        }

        return repository.save(feedback);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFeedback(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
