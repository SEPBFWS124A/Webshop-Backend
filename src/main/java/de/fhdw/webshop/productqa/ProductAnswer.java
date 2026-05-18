package de.fhdw.webshop.productqa;

import de.fhdw.webshop.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "product_answers")
@Getter
@Setter
@NoArgsConstructor
public class ProductAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private ProductQuestion question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "answer_text", nullable = false, length = 500)
    private String answerText;

    @Column(name = "official_answer", nullable = false)
    private boolean officialAnswer = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "official_marked_by_user_id")
    private User officialMarkedByUser;

    @Column(name = "official_marked_at")
    private Instant officialMarkedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
