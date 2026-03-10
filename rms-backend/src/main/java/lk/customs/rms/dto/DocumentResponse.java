package lk.customs.rms.dto;

import lk.customs.rms.entity.Document;
import lk.customs.rms.enums.Priority;
import lk.customs.rms.enums.Status;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class DocumentResponse {

    private Long id;
    private String refNo;
    private String title;
    private LocalDate receivedDate;
    private String companyName;
    private Priority priority;
    private Status status;

    private Long createdByUserId;
    private String createdByName;

    private Long currentOwnerUserId;
    private String currentOwnerName;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime issuedAt;

    private String mainAttachmentType;

    public static DocumentResponse from(Document d, String createdByName, String ownerName) {
        return from(d, createdByName, ownerName, null);
    }

    public static DocumentResponse from(Document d, String createdByName, String ownerName, String mainAttachmentType) {
        return DocumentResponse.builder()
                .id(d.getId())
                .refNo(d.getRefNo())
                .title(d.getTitle())
                .receivedDate(d.getReceivedDate())
                .companyName(d.getCompanyName())
                .priority(d.getPriority())
                .status(d.getStatus())
                .createdByUserId(d.getCreatedByUserId())
                .createdByName(createdByName)
                .currentOwnerUserId(d.getCurrentOwnerUserId())
                .currentOwnerName(ownerName)
                .createdAt(d.getCreatedAt())
                .completedAt(d.getCompletedAt())
                .issuedAt(d.getIssuedAt())
                .mainAttachmentType(mainAttachmentType)
                .build();
    }
}
