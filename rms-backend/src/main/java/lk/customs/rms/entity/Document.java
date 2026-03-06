package lk.customs.rms.entity;

import jakarta.persistence.*;
import lk.customs.rms.enums.Priority;
import lk.customs.rms.enums.Status;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Getter
@Setter
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="ref_no", unique = true, nullable = false)
    private String refNo;

    @Column(nullable = false)
    private String title;

    @Column(name="received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name="company_name")
    private String companyName;

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

    @Column(name="completed_at")
    private LocalDateTime completedAt;

    @Column(name="issued_at")
    private LocalDateTime issuedAt;

    // soft delete
    @Column(name="is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name="deleted_at")
    private LocalDateTime deletedAt;

    @Column(name="deleted_by_user_id")
    private Long deletedByUserId;
}
