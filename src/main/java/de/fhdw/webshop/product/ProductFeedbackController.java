package de.fhdw.webshop.product;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/product-feedback")
public class ProductFeedbackController {
    private final ProductFeedbackRepository repository;

    
    public ProductFeedbackController(ProductFeedbackRepository repository) {
        this.repository = repository;
    }

@PostMapping
public ProductFeedback createFeedback(@RequestBody ProductFeedback feedback) {
    return repository.save(feedback);
}

    @GetMapping
    public List<ProductFeedback> getAllFeedback() {
        return repository.findAll();
    }
}