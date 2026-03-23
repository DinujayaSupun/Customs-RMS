package lk.customs.rms.service;

import lk.customs.rms.dto.DcAutoForwardConfigResponse;
import lk.customs.rms.dto.UpdateDcAutoForwardConfigRequest;
import lk.customs.rms.entity.DcAutoForwardConfig;

public interface DcAutoForwardConfigService {
    DcAutoForwardConfigResponse getConfig();
    DcAutoForwardConfigResponse updateConfig(UpdateDcAutoForwardConfigRequest request);
    DcAutoForwardConfig getOrCreateEntity();
}
