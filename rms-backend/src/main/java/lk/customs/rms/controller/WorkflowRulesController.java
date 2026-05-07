package lk.customs.rms.controller;

import lk.customs.rms.dto.WorkflowRulesResponse;
import lk.customs.rms.enums.Status;
import lk.customs.rms.service.DcAutoForwardConfigService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
@RequestMapping("/api/workflow-rules")
public class WorkflowRulesController {

    private final DcAutoForwardConfigService dcAutoForwardConfigService;

    public WorkflowRulesController(DcAutoForwardConfigService dcAutoForwardConfigService) {
        this.dcAutoForwardConfigService = dcAutoForwardConfigService;
    }

    @GetMapping
    public WorkflowRulesResponse getWorkflowRules() {
        return WorkflowRulesResponse.builder()
                .forwardReturnAllowedStatuses(dcAutoForwardConfigService.getForwardReturnAllowedStatuses()
                        .stream()
                        .map(Status::name)
                        .toList())
                .approveRejectButtonsEnabled(dcAutoForwardConfigService.isApproveRejectButtonsEnabled())
                .build();
    }
}
