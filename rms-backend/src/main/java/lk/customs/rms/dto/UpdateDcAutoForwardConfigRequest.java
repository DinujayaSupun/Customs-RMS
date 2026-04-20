package lk.customs.rms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateDcAutoForwardConfigRequest {

    @NotNull
    private Boolean enabled;

    @NotNull
    @Min(1)
    @Max(10080)
    private Integer timeoutMinutes;

    private Long receiverUserId;

    private List<String> forwardReturnAllowedStatuses;
}
