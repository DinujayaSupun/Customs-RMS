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
    private Boolean canUndoSend;
    private Boolean canDelete;
    private String undoSendStatus;
    private LocalDateTime undoSendExpiresAt;
    private Boolean undoSendReceiverOpened;
    private String undoSendActionType;
    private Boolean undoSendRequiresReason;
    private Boolean undoSendShowExpiredInfo;
    private Long undoSendByUserId;
    private String undoSendByName;
    private String undoSendByRole;
    private Long undoSendFromUserId;
    private String undoSendFromName;
    private String undoSendFromRole;

    public static DocumentResponse from(Mapping mapping) {
        Document d = mapping.document;
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
                .createdByName(mapping.createdByName)
                .currentOwnerUserId(d.getCurrentOwnerUserId())
                .currentOwnerName(mapping.ownerName)
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .inboxReceivedAt(mapping.inboxReceivedAt)
                .inboxSenderUserId(mapping.inboxSenderUserId)
                .inboxSenderName(mapping.inboxSenderName)
                .inboxSenderRole(mapping.inboxSenderRole)
                .completedAt(d.getCompletedAt())
                .issuedAt(d.getIssuedAt())
                .mainAttachmentType(mapping.mainAttachmentType)
                .latestRemarkPreview(mapping.latestRemarkPreview)
                .latestRemarkByUserId(mapping.latestRemarkByUserId)
                .latestRemarkByName(mapping.latestRemarkByName)
                .latestRemarkByRole(mapping.latestRemarkByRole)
                .latestRemarkTextPreview(mapping.latestRemarkTextPreview)
                .latestRemarkText(mapping.latestRemarkText)
                .latestRemarkAt(mapping.latestRemarkAt)
                .viewedByMe(mapping.viewedByMe)
                .canUndoSend(mapping.canUndoSend)
                .canDelete(mapping.canDelete)
                .undoSendStatus(mapping.undoSendStatus)
                .undoSendExpiresAt(mapping.undoSendExpiresAt)
                .undoSendReceiverOpened(mapping.undoSendReceiverOpened)
                .undoSendActionType(mapping.undoSendActionType)
                .undoSendRequiresReason(mapping.undoSendRequiresReason)
                .undoSendShowExpiredInfo(mapping.undoSendShowExpiredInfo)
                .undoSendByUserId(mapping.undoSendByUserId)
                .undoSendByName(mapping.undoSendByName)
                .undoSendByRole(mapping.undoSendByRole)
                .undoSendFromUserId(mapping.undoSendFromUserId)
                .undoSendFromName(mapping.undoSendFromName)
                .undoSendFromRole(mapping.undoSendFromRole)
                .build();
    }

    public static Mapping.MappingBuilder mapping(Document document) {
        return Mapping.builder().document(document);
    }

    @Getter
    @Builder
    public static class Mapping {
        private Document document;
        private String createdByName;
        private String ownerName;
        private String mainAttachmentType;
        private String latestRemarkPreview;
        @Builder.Default
        private Boolean viewedByMe = false;
        private LocalDateTime inboxReceivedAt;
        private Long inboxSenderUserId;
        private String inboxSenderName;
        private String inboxSenderRole;
        private Long latestRemarkByUserId;
        private String latestRemarkByName;
        private String latestRemarkByRole;
        private String latestRemarkTextPreview;
        private String latestRemarkText;
        private LocalDateTime latestRemarkAt;
        @Builder.Default
        private Boolean canUndoSend = false;
        @Builder.Default
        private Boolean canDelete = false;
        private String undoSendStatus;
        private LocalDateTime undoSendExpiresAt;
        @Builder.Default
        private Boolean undoSendReceiverOpened = false;
        private String undoSendActionType;
        @Builder.Default
        private Boolean undoSendRequiresReason = false;
        @Builder.Default
        private Boolean undoSendShowExpiredInfo = false;
        private Long undoSendByUserId;
        private String undoSendByName;
        private String undoSendByRole;
        private Long undoSendFromUserId;
        private String undoSendFromName;
        private String undoSendFromRole;
    }
}
