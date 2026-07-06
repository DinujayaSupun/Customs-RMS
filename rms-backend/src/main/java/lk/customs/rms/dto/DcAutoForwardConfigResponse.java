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
    // Per-DC receiver mapping: which DDC/SDDC each individual DC's timed-out documents go to.
    // Replaces the single receiverUserId above when populated (kept for backward compatibility).
    private List<DcAutoForwardReceiverEntry> dcReceivers;
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
