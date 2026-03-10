package lk.customs.rms.controller;

import lk.customs.rms.dto.AuditLogResponse;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/documents/{documentId}/audit-logs")
@PreAuthorize("hasAnyRole('ADMIN','DC')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    public AuditLogController(AuditLogRepository auditLogRepository,
                              UserRepository userRepository,
                              DocumentRepository documentRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
    }

    @GetMapping
    public List<AuditLogResponse> getAuditHistory(@PathVariable Long documentId) {
        String documentRef = documentRepository.findByIdAndDeletedFalse(documentId).map(d -> d.getRefNo()).orElse(null);
        return auditLogRepository.findHistoryForDocument(documentId)
                .stream()
                .map(l -> AuditLogResponse.builder()
                        .id(l.getId())
                        .entityType(l.getEntityType())
                        .entityId(l.getEntityId())
                .documentRef(documentRef)
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
