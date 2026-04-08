package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PermissionMatrixResponse {
    private List<String> roles;
    private List<String> permissions;
    private List<RolePermissionEntryResponse> entries;
}