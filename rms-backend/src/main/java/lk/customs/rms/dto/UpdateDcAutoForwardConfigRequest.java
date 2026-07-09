package lk.customs.rms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateDcAutoForwardConfigRequest {

    @NotNull
    private Boolean enabled;

    @NotNull
    @Min(1)
    @Max(10080)
    private Integer timeoutMinutes;

    // Kept for backward compatibility; no longer required or used by the scheduler once per-DC
    // mappings (dcReceivers below) are configured.
    private Long receiverUserId;

    // Per-DC receiver mapping to save. A DC omitted from this list has its mapping removed
    // (reverts to "no receiver configured", so its documents are skipped by the scheduler).
    @Valid
    private List<Entry> dcReceivers;

    private List<String> forwardReturnAllowedStatuses;

    @NotNull
    private Boolean approveRejectButtonsEnabled;

    private Boolean undoSendEnabled;

    @Min(1)
    @Max(168)
    private Integer undoSendWindowHours;

    private Boolean undoSendRequiresUnopened;

    private List<String> undoSendAllowedActions;

    private Boolean undoSendRequiresReason;

    private Boolean undoSendNotifyReceiver;

    private Boolean undoSendShowExpiredInfo;

    @Getter
    @Setter
    public static class Entry {
        @NotNull
        private Long dcUserId;

        @NotNull
        private Long receiverUserId;
    }
}
