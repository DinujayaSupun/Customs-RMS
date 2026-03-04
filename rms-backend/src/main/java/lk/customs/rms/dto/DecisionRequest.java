package lk.customs.rms.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DecisionRequest {
    // ✅ Optional remark to save before approve/reject/issue
    private String remarkText;
}
