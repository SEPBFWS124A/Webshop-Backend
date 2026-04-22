package de.fhdw.webshop.alerting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "known_email_addresses")
@Getter
@Setter
@NoArgsConstructor
public class KnownEmailAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String email;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    public KnownEmailAddress(String label, String email, boolean isDefault) {
        this.label = label;
        this.email = email;
        this.isDefault = isDefault;
    }
}
