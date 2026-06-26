package lk.customs.rms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_remarks", indexes = {
    // Covers document_id lookups, the ordered-by-remarked-at list, and the latest-remark
    // (max remarked_at per document) correlated subqueries used when rendering inbox/sent lists.
    @Index(name = "idx_remarks_document_remarked_at", columnList = "document_id,remarked_at"),
    // Covers the "is this my latest minute in the current period" check (canModifyRemark).
    @Index(name = "idx_remarks_document_user_remarked_at", columnList = "document_id,remarked_by,remarked_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentRemark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "remark_id")
    private Long id;

    // Keep FK field for easy queries (works with insertable/updatable=false mapping below)
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "remark_text", nullable = false)
    private String remarkText;

    @Column(name = "remarked_by", nullable = false)
    private Long remarkedByUserId;

    @Column(name = "remarked_at", nullable = false)
    private LocalDateTime remarkedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", insertable = false, updatable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remarked_by", insertable = false, updatable = false)
    private User remarkedBy;
}
