package lk.customs.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_performed_at_id", columnList = "performed_at,id"),
    @Index(name = "idx_audit_action_performed_at", columnList = "action_type,performed_at"),
    @Index(name = "idx_audit_user_performed_at", columnList = "performed_by_user_id,performed_at"),
    @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id")
})
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

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name="message")
    private String message;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name="details_json")
    private String detailsJson;
}
