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

    private String forwardVisibility;

    private Long actionByUserId;
    private String actionByUserName;

    private LocalDateTime actionAt;
}
