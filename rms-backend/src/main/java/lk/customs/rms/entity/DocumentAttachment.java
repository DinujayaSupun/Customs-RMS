package lk.customs.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_attachments")
@Getter
@Setter
public class DocumentAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="document_id", nullable = false)
    private Long documentId;

    // Original filename the user uploaded (example: letter.pdf)
    @Column(name="file_name", nullable = false)
    private String fileName;

    // Where we saved the file (relative path from upload-dir)
    @Column(name="file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name="version_no", nullable = false)
    private Integer versionNo;

    @Column(name="is_latest", nullable = false)
    private Boolean isLatest;

    @Column(name="uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name="uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name="is_deleted", nullable = false)
    private Boolean deleted;

    @Column(name="deleted_at")
    private LocalDateTime deletedAt;

    @Column(name="deleted_by")
    private Long deletedBy;
}
