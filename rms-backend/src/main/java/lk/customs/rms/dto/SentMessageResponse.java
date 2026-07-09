package lk.customs.rms.dto;

import lk.customs.rms.enums.Priority;
import lk.customs.rms.enums.Status;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SentMessageResponse {
    private Long movementId;
    private Long documentId;
    private String refNo;
    private String title;
    private String companyName;
    private Priority priority;
    private Status status;
    private String mainAttachmentType;
    private String forwardVisibility;
    private Long toUserId;
    private String toUserName;
    private Long toGroupId;
    private String toGroupName;
    private RecipientSummaryResponse recipientSummary;
    private String recipientSummaryText;
    private String latestRemarkPreview;
    private LocalDateTime sentAt;
    private boolean autoForwarded;
    private boolean canUndoSend;
    private String undoSendStatus;
    private LocalDateTime undoSendExpiresAt;
    private boolean undoSendReceiverOpened;
    private String undoSendActionType;
    private boolean undoSendRequiresReason;
    private boolean undoSendShowExpiredInfo;
    private Long undoSendByUserId;
    private String undoSendByName;
    private String undoSendByRole;
}
