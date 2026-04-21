package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PermissionsPageSaveResponse {
    private PermissionMatrixResponse permissionMatrix;
    private DcAutoForwardConfigResponse dcAutoForwardConfig;
}
