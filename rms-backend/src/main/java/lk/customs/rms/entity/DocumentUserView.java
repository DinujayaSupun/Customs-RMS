package lk.customs.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "document_user_views",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_document_user_view", columnNames = {"document_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_document_user_view_document", columnList = "document_id"),
                @Index(name = "idx_document_user_view_user", columnList = "user_id")
        }
)
@Getter
@Setter
public class DocumentUserView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;
}
