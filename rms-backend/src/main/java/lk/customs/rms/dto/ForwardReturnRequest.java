package lk.customs.rms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ForwardReturnRequest {

    @NotNull
    private Long toUserId;

    // Required for forward action: PRIVATE or PUBLIC
    private String forwardVisibility;

    // ✅ Optional remark to save before forward/return
    private String remarkText;

    @Size(max = 50, message = "CC recipients cannot exceed 50")
    private List<Long> ccUserIds;

    @Size(max = 50, message = "BCC recipients cannot exceed 50")
    private List<Long> bccUserIds;
}
