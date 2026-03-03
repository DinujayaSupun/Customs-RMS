package lk.customs.rms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_remarks")
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

    @Column(name = "remark_text", nullable = false, columnDefinition = "TEXT")
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
