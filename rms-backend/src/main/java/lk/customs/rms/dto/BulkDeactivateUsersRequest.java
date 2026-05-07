package lk.customs.rms.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkDeactivateUsersRequest {

    @NotEmpty(message = "Select at least one user to deactivate.")
    private List<@NotNull(message = "User ID is required.") Long> userIds;

    @NotNull(message = "Fallback DC user ID is required.")
    private Long fallbackDcUserId;
}
