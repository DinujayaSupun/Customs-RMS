package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DcAutoForwardConfigResponse {
    private Boolean enabled;
    private Integer timeoutMinutes;
    private Long receiverUserId;
    private String receiverName;
    private String receiverRole;
    private List<String> forwardReturnAllowedStatuses;
    private Boolean approveRejectButtonsEnabled;
    private Boolean undoSendEnabled;
    private Integer undoSendWindowHours;
    private Boolean undoSendRequiresUnopened;
    private List<String> undoSendAllowedActions;
    private Boolean undoSendRequiresReason;
    private Boolean undoSendNotifyReceiver;
    private Boolean undoSendShowExpiredInfo;
}
