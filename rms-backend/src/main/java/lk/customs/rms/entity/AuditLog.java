package lk.customs.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="entity_type", nullable = false)
    private String entityType; // DOCUMENT / ATTACHMENT / MOVEMENT / REMARK

    @Column(name="entity_id", nullable = false)
    private Long entityId;

    @Column(name="action_type", nullable = false)
    private String actionType; // CREATE / UPDATE / DELETE / FORWARD / RETURN / APPROVE / REJECT / ISSUE / UPLOAD / NEW_VERSION

    @Column(name="performed_by_user_id", nullable = false)
    private Long performedByUserId;

    @Column(name="performed_at", nullable = false)
    private LocalDateTime performedAt;

    @Column(name="message", columnDefinition = "TEXT")
    private String message;

    @Column(name="details_json", columnDefinition = "TEXT")
    private String detailsJson;
}
