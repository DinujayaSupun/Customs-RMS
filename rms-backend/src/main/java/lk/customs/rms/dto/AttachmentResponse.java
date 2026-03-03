package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AttachmentResponse {
    private Long id;
    private Long documentId;

    private String fileName;
    private String filePath;

    private Integer versionNo;
    private Boolean isLatest;

    private Long uploadedBy;
    private String uploadedByName;

    private LocalDateTime uploadedAt;

    private Boolean isDeleted;
    private LocalDateTime deletedAt;
    private Long deletedBy;
    private String deletedByName;
}
