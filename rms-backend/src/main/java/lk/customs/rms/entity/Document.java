package lk.customs.rms.entity;

import jakarta.persistence.*;
import lk.customs.rms.enums.DocumentType;
import lk.customs.rms.enums.Priority;
import lk.customs.rms.enums.Status;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_documents_owner_status_deleted_updated", columnList = "current_owner_user_id,status,is_deleted,updated_at"),
    @Index(name = "idx_documents_deleted_updated_id", columnList = "is_deleted,updated_at,id"),
    @Index(name = "idx_documents_created_by_deleted", columnList = "created_by_user_id,is_deleted"),
    @Index(name = "idx_documents_received_date", columnList = "received_date"),
    @Index(name = "idx_documents_visibility_deleted", columnList = "visibility,is_deleted")
})
@Getter
@Setter
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Optimistic locking: concurrent updates to the same document (e.g. the auto-forward scheduler
    // firing while the owner acts) are detected instead of silently overwriting each other.
    // Default 0L so Spring Data 4 / Hibernate 7 never calls persist() on an existing row.
    @Version
    @Column(name = "version")
    private Long version = 0L;

    @Column(name="ref_no", unique = true, nullable = false)
    private String refNo;

    @Column(nullable = false)
    private String title;

    @Column(name="received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name="company_name")
    private String companyName;

    // Internal vs External origin, chosen at creation. Nullable so pre-existing rows validate.
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", length = 20)
    private DocumentType documentType;

    @Column(name = "visibility", length = 10)
    private String visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name="created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name="current_owner_user_id", nullable = false)
    private Long currentOwnerUserId;

    @Column(name="created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name="updated_at")
    private LocalDateTime updatedAt;

    @Column(name="completed_at")
    private LocalDateTime completedAt;

    @Column(name="issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "dc_assigned_at")
    private LocalDateTime dcAssignedAt;

    @Column(name = "dc_viewed_at")
    private LocalDateTime dcViewedAt;

    // soft delete
    @Column(name="is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name="deleted_at")
    private LocalDateTime deletedAt;

    @Column(name="deleted_by_user_id")
    private Long deletedByUserId;
}
