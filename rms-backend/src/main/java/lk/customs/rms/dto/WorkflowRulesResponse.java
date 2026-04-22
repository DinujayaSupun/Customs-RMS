package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WorkflowRulesResponse {
    private List<String> forwardReturnAllowedStatuses;
    private Boolean approveRejectButtonsEnabled;
}
