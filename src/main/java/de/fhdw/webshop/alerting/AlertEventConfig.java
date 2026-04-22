package de.fhdw.webshop.alerting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "alert_event_configs")
@Getter
@Setter
@NoArgsConstructor
public class AlertEventConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, unique = true)
    private AlertEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecipientStrategy strategy;

    @Column(nullable = false)
    private boolean enabled;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "alert_event_config_recipients",
            joinColumns = @JoinColumn(name = "config_id"),
            inverseJoinColumns = @JoinColumn(name = "email_id")
    )
    private List<KnownEmailAddress> recipients = new ArrayList<>();

    public AlertEventConfig(AlertEventType eventType, RecipientStrategy strategy, boolean enabled) {
        this.eventType = eventType;
        this.strategy = strategy;
        this.enabled = enabled;
    }
}
