package lk.customs.rms.controller;

import lk.customs.rms.dto.AuditLogResponse;
import lk.customs.rms.entity.AuditLog;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.PermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@CrossOrigin
@RequestMapping("/api/documents/{documentId}/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
        private final CurrentUserService currentUserService;
        private final PermissionService permissionService;

    public AuditLogController(AuditLogRepository auditLogRepository,
                              UserRepository userRepository,
                                                          DocumentRepository documentRepository,
                                                          CurrentUserService currentUserService,
                                                          PermissionService permissionService) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
                this.currentUserService = currentUserService;
                this.permissionService = permissionService;
    }

    @GetMapping
        public List<AuditLogResponse> getAuditHistory(@PathVariable Long documentId, Authentication authentication) {
                Long actorUserId = currentUserService.requireUserId(authentication);
                if (!permissionService.hasPermission(actorUserId, AppPermission.VIEW_LOGS)) {
                        throw new BadRequestException("You are not allowed to view logs.");
                }

        String documentRef = documentRepository.findByIdAndDeletedFalse(documentId).map(d -> d.getRefNo()).orElse(null);
        List<AuditLog> logs = auditLogRepository.findHistoryForDocument(documentId);
        Map<Long, User> usersById = usersByIdForLogs(logs);

        return logs
                .stream()
                .map(l -> AuditLogResponse.builder()
                        .id(l.getId())
                        .entityType(l.getEntityType())
                        .entityId(l.getEntityId())
                .documentRef(documentRef)
                        .actionType(l.getActionType())
                        .performedByUserId(l.getPerformedByUserId())
                        .performedByUserName(userName(usersById, l.getPerformedByUserId()))
                        .performedAt(l.getPerformedAt())
                        .message(l.getMessage())
                        .detailsJson(l.getDetailsJson())
                        .build())
                .toList();
    }

    private Map<Long, User> usersByIdForLogs(List<AuditLog> logs) {
        Set<Long> userIds = new HashSet<>();
        for (AuditLog log : logs) {
            if (log.getPerformedByUserId() != null) {
                userIds.add(log.getPerformedByUserId());
            }
        }

        if (userIds.isEmpty()) return Map.of();

        return userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private String userName(Map<Long, User> usersById, Long userId) {
        User user = userId == null ? null : usersById.get(userId);
        return user == null ? null : user.getFullName();
    }
}
