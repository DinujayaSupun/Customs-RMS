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
    private String visibility;
    private Priority priority;
    private Status status;

    private Long createdByUserId;
    private String createdByName;

    private Long currentOwnerUserId;
    private String currentOwnerName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime inboxReceivedAt;
    private Long inboxSenderUserId;
    private String inboxSenderName;
    private String inboxSenderRole;
    private LocalDateTime completedAt;
    private LocalDateTime issuedAt;

    private String mainAttachmentType;
    private String latestRemarkPreview;
    private Long latestRemarkByUserId;
    private String latestRemarkByName;
    private String latestRemarkByRole;
    private String latestRemarkTextPreview;
    private String latestRemarkText;
    private LocalDateTime latestRemarkAt;
    private Boolean viewedByMe;

    public static DocumentResponse from(Document d, String createdByName, String ownerName) {
        return from(d, createdByName, ownerName, null, null, false, null);
    }

    public static DocumentResponse from(Document d, String createdByName, String ownerName, String mainAttachmentType) {
        return from(d, createdByName, ownerName, mainAttachmentType, null, false, null);
    }

    public static DocumentResponse from(Document d,
                                        String createdByName,
                                        String ownerName,
                                        String mainAttachmentType,
                                        boolean viewedByMe) {
        return from(d, createdByName, ownerName, mainAttachmentType, null, viewedByMe, null);
    }

    public static DocumentResponse from(Document d,
                                        String createdByName,
                                        String ownerName,
                                        String mainAttachmentType,
                                        String latestRemarkPreview,
                                        boolean viewedByMe) {
        return from(d, createdByName, ownerName, mainAttachmentType, latestRemarkPreview, viewedByMe, null);
    }

    public static DocumentResponse from(Document d,
                                        String createdByName,
                                        String ownerName,
                                        String mainAttachmentType,
                                        String latestRemarkPreview,
                                        boolean viewedByMe,
                                        LocalDateTime inboxReceivedAt) {
        return from(d, createdByName, ownerName, mainAttachmentType, latestRemarkPreview, viewedByMe, inboxReceivedAt,
                null, null, null, null, null, null, null, null, null);
    }

    public static DocumentResponse from(Document d,
                                        String createdByName,
                                        String ownerName,
                                        String mainAttachmentType,
                                        String latestRemarkPreview,
                                        boolean viewedByMe,
                                        LocalDateTime inboxReceivedAt,
                                        Long inboxSenderUserId,
                                        String inboxSenderName,
                                        String inboxSenderRole,
                                        Long latestRemarkByUserId,
                                        String latestRemarkByName,
                                        String latestRemarkByRole,
                                        String latestRemarkTextPreview,
                                        String latestRemarkText,
                                        LocalDateTime latestRemarkAt) {
        return DocumentResponse.builder()
                .id(d.getId())
                .refNo(d.getRefNo())
                .title(d.getTitle())
                .receivedDate(d.getReceivedDate())
                .companyName(d.getCompanyName())
                .visibility(d.getVisibility())
                .priority(d.getPriority())
                .status(d.getStatus())
                .createdByUserId(d.getCreatedByUserId())
                .createdByName(createdByName)
                .currentOwnerUserId(d.getCurrentOwnerUserId())
                .currentOwnerName(ownerName)
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .inboxReceivedAt(inboxReceivedAt)
                .inboxSenderUserId(inboxSenderUserId)
                .inboxSenderName(inboxSenderName)
                .inboxSenderRole(inboxSenderRole)
                .completedAt(d.getCompletedAt())
                .issuedAt(d.getIssuedAt())
                .mainAttachmentType(mainAttachmentType)
                .latestRemarkPreview(latestRemarkPreview)
                .latestRemarkByUserId(latestRemarkByUserId)
                .latestRemarkByName(latestRemarkByName)
                .latestRemarkByRole(latestRemarkByRole)
                .latestRemarkTextPreview(latestRemarkTextPreview)
                .latestRemarkText(latestRemarkText)
                .latestRemarkAt(latestRemarkAt)
                .viewedByMe(viewedByMe)
                .build();
    }
}
