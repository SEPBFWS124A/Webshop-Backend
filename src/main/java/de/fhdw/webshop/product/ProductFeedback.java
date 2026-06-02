package de.fhdw.webshop.product;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ProductFeedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long productId;
    private Long userId;
    private Integer rating;
    @Column(length = 1000)
    private String comment;
    private LocalDateTime createdAt = LocalDateTime.now();
    private String source;
    private Boolean approved = false;
}
