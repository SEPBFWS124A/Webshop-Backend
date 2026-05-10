package de.fhdw.webshop.sellerapplication;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "seller_applications")
@Getter
@Setter
@NoArgsConstructor
public class SellerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false, length = 180)
    private String companyName;

    @Column(name = "contact_name", nullable = false, length = 180)
    private String contactName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 60)
    private String phone;

    @Column(length = 255)
    private String website;

    @Column(name = "product_category", nullable = false, length = 120)
    private String productCategory;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SellerApplicationStatus status = SellerApplicationStatus.RECEIVED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
