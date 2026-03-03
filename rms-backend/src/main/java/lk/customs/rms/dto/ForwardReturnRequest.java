package lk.customs.rms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForwardReturnRequest {

    @NotNull
    private Long toUserId;

    @NotNull
    private Long actionByUserId;

    // ✅ Optional remark to save before forward/return
    private String remarkText;
}
