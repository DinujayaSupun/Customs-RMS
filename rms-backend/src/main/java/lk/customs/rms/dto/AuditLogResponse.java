package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AuditLogResponse {
    private Long id;
    private String entityType;
    private Long entityId;
    private String documentRef;
    private String actionType;

    private Long performedByUserId;
    private String performedByUserName;

    private LocalDateTime performedAt;

    private String message;
    private String detailsJson;
}
