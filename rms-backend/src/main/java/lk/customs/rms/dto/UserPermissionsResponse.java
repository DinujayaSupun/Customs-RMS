package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * A user's effective permissions: for each permission, the role default, the per-user override
 * (null = inherit), and the resulting effective value the app enforces.
 */
@Getter
@Builder
public class UserPermissionsResponse {

    private Long userId;
    private String username;
    private String roleName;
    private List<Entry> entries;

    @Getter
    @Builder
    public static class Entry {
        private String permission;
        private boolean roleDefault;
        // null = inherit role; true = granted override; false = revoked override
        private Boolean override;
        private boolean effective;
    }
}
