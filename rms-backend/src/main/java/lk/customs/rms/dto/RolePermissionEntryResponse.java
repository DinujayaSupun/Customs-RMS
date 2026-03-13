package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RolePermissionEntryResponse {
    private String roleName;
    private String permission;
    private Boolean enabled;
}