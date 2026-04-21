package lk.customs.rms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionsPageSaveRequest {

    @NotNull
    @Valid
    private UpdatePermissionMatrixRequest permissionMatrix;

    @NotNull
    @Valid
    private UpdateDcAutoForwardConfigRequest dcAutoForwardConfig;
}
