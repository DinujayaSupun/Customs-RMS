package lk.customs.rms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Sets per-user permission overrides. For each entry, {@code override} is tri-state:
 * true = GRANT, false = REVOKE, null = INHERIT (removes any existing override for that permission).
 */
@Getter
@Setter
public class UpdateUserPermissionsRequest {

    @NotNull
    @Valid
    private List<PermissionEntry> entries;

    @Getter
    @Setter
    public static class PermissionEntry {
        @NotNull
        private String permission;

        // Nullable on purpose: null means "inherit role" (delete any override row).
        private Boolean override;
    }
}
