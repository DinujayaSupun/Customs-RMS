package lk.customs.rms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DecisionRequest {

    @NotNull
    private Long actionByUserId;

    // ✅ Optional remark to save before approve/reject/issue
    private String remarkText;
}
