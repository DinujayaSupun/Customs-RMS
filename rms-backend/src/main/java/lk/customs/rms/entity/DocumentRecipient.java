package lk.customs.rms.entity;

import jakarta.persistence.*;
import lk.customs.rms.enums.RecipientType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_recipients", indexes = {
        @Index(name = "idx_recipients_set", columnList = "recipient_set_id"),
        @Index(name = "idx_recipients_document_user", columnList = "document_id,user_id"),
        @Index(name = "idx_recipients_user_type", columnList = "user_id,recipient_type"),
        @Index(name = "idx_recipients_document_type", columnList = "document_id,recipient_type")
})
@Getter
@Setter
public class DocumentRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_set_id", nullable = false)
    private Long recipientSetId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 8)
    private RecipientType recipientType;

    @Column(name = "added_by_user_id", nullable = false)
    private Long addedByUserId;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;
}
