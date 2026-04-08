package lk.customs.rms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdatePermissionMatrixRequest {

    @NotNull
    @Valid
    private List<PermissionEntry> entries;

    @Getter
    @Setter
    public static class PermissionEntry {
        @NotNull
        private String roleName;

        @NotNull
        private String permission;

        @NotNull
        private Boolean enabled;
    }
}