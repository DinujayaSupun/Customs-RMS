package lk.customs.rms.service;

import lk.customs.rms.dto.DcAutoForwardConfigResponse;
import lk.customs.rms.dto.UpdateDcAutoForwardConfigRequest;
import lk.customs.rms.entity.DcAutoForwardConfig;
import lk.customs.rms.enums.MovementActionType;
import lk.customs.rms.enums.Status;

import java.util.List;
import java.util.Map;

public interface DcAutoForwardConfigService {
    DcAutoForwardConfigResponse getConfig();
    DcAutoForwardConfigResponse updateConfig(UpdateDcAutoForwardConfigRequest request);
    DcAutoForwardConfig getOrCreateEntity();
    List<Status> getForwardReturnAllowedStatuses();
    boolean isForwardReturnAllowed(Status status);
    boolean isApproveRejectButtonsEnabled();
    List<MovementActionType> getUndoSendAllowedActions();
    /** dcUserId -> receiverUserId. Used by the scheduler; a DC missing from the map has no receiver configured. */
    Map<Long, Long> getDcReceiverMapping();
}
