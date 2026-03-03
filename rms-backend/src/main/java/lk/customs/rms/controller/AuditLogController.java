package lk.customs.rms.controller;

import lk.customs.rms.dto.AuditLogResponse;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/documents/{documentId}/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogController(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<AuditLogResponse> getAuditHistory(@PathVariable Long documentId) {
        return auditLogRepository.findHistoryForDocument(documentId)
                .stream()
                .map(l -> AuditLogResponse.builder()
                        .id(l.getId())
                        .entityType(l.getEntityType())
                        .entityId(l.getEntityId())
                        .actionType(l.getActionType())
                        .performedByUserId(l.getPerformedByUserId())
                        .performedByUserName(userRepository.findById(l.getPerformedByUserId()).map(u -> u.getFullName()).orElse(null))
                        .performedAt(l.getPerformedAt())
                        .message(l.getMessage())
                        .detailsJson(l.getDetailsJson())
                        .build())
                .toList();
    }
}
