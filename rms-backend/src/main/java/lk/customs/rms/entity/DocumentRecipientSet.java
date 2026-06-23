package lk.customs.rms.entity;

import jakarta.persistence.*;
import lk.customs.rms.enums.RecipientSetReason;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_recipient_sets", indexes = {
        @Index(name = "idx_recipient_sets_document_active", columnList = "document_id,is_active"),
        @Index(name = "idx_recipient_sets_document_created", columnList = "document_id,created_at,id"),
        @Index(name = "idx_recipient_sets_movement", columnList = "movement_id")
})
@Getter
@Setter
public class DocumentRecipientSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "movement_id")
    private Long movementId;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private RecipientSetReason reason;
}
