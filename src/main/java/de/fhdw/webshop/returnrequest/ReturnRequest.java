package de.fhdw.webshop.returnrequest;

import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "return_reason")
    private ReturnReason reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "return_request_status")
    private ReturnRequestStatus status = ReturnRequestStatus.SUBMITTED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "defect_description", length = 500)
    private String defectDescription;

    @Column(name = "label_created_at", nullable = false)
    private Instant labelCreatedAt = Instant.now();

    @Column(name = "carrier_name", nullable = false, length = 80)
    private String carrierName;

    @Column(name = "tracking_id", nullable = false, unique = true, length = 80)
    private String trackingId;

    @Column(name = "qr_code_payload", nullable = false, length = 500)
    private String qrCodePayload;

    @Column(name = "sender_name", nullable = false, length = 255)
    private String senderName;

    @Column(name = "sender_street", nullable = false, length = 255)
    private String senderStreet;

    @Column(name = "sender_postal_code", nullable = false, length = 20)
    private String senderPostalCode;

    @Column(name = "sender_city", nullable = false, length = 100)
    private String senderCity;

    @Column(name = "sender_country", nullable = false, length = 100)
    private String senderCountry;

    @Column(name = "return_center_name", nullable = false, length = 255)
    private String returnCenterName;

    @Column(name = "return_center_street", nullable = false, length = 255)
    private String returnCenterStreet;

    @Column(name = "return_center_postal_code", nullable = false, length = 20)
    private String returnCenterPostalCode;

    @Column(name = "return_center_city", nullable = false, length = 100)
    private String returnCenterCity;

    @Column(name = "return_center_country", nullable = false, length = 100)
    private String returnCenterCountry;

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReturnRequestItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReturnRequestImage> defectImages = new ArrayList<>();
}
