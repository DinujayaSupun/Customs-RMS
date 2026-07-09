package lk.customs.rms.dto;

import lk.customs.rms.enums.MovementActionType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MovementResponse {
    private Long id;
    private Long documentId;
    private MovementActionType actionType;

    private Long fromUserId;
    private String fromUserName;

    private Long toUserId;
    private String toUserName;

    // Set only when this FORWARD targeted a group; toUserId/toUserName still resolve to the
    // group's anchor admin (see Document#currentOwnerGroupId).
    private Long toGroupId;
    private String toGroupName;

    private String forwardVisibility;
    private RecipientSummaryResponse recipientSummary;

    private Long actionByUserId;
    private String actionByUserName;

    private LocalDateTime actionAt;
}
