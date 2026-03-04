package lk.customs.rms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MergeUsersRequest {
    @NotNull
    private Long sourceUserId;

    @NotNull
    private Long targetUserId;
}
