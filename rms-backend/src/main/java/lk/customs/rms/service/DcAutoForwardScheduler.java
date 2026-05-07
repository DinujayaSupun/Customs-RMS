package lk.customs.rms.service;

import lk.customs.rms.entity.DcAutoForwardConfig;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentMovement;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.MovementActionType;
import lk.customs.rms.enums.Status;
import lk.customs.rms.repository.DocumentMovementRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.DocumentUserViewRepository;
import lk.customs.rms.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DcAutoForwardScheduler {

    private final DcAutoForwardConfigService dcAutoForwardConfigService;
    private final DocumentRepository documentRepository;
    private final DocumentMovementRepository movementRepository;
    private final DocumentUserViewRepository documentUserViewRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public DcAutoForwardScheduler(DcAutoForwardConfigService dcAutoForwardConfigService,
                                  DocumentRepository documentRepository,
                                  DocumentMovementRepository movementRepository,
                                  DocumentUserViewRepository documentUserViewRepository,
                                  UserRepository userRepository,
                                  AuditLogService auditLogService) {
        this.dcAutoForwardConfigService = dcAutoForwardConfigService;
        this.documentRepository = documentRepository;
        this.movementRepository = movementRepository;
        this.documentUserViewRepository = documentUserViewRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Scheduled(fixedDelayString = "${app.dc-auto-forward.poll-ms:60000}")
    @Transactional
    public void processTimedOutDcDocuments() {
        DcAutoForwardConfig config = dcAutoForwardConfigService.getOrCreateEntity();
        if (!config.isEnabled() || config.getReceiverUserId() == null) {
            return;
        }

        User receiver = userRepository.findById(config.getReceiverUserId()).orElse(null);
        if (receiver == null || !Boolean.TRUE.equals(receiver.getIsActive()) || receiver.getRole() == null) {
            return;
        }
        String receiverRole = receiver.getRole().getRoleName();
        if (!"DDC".equalsIgnoreCase(receiverRole) && !"SDDC".equalsIgnoreCase(receiverRole)) {
            return;
        }

        List<User> dcUsers = userRepository.findByIsActiveTrueAndRole_RoleNameOrderByFullNameAsc("DC");
        if (dcUsers.isEmpty()) return;
        List<Long> dcUserIds = dcUsers.stream().map(User::getId).toList();

        List<Document> candidates = documentRepository.findPendingDcAutoForwardCandidates(dcUserIds);
        if (candidates.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        for (Document doc : candidates) {
            if (doc.getDcAssignedAt() == null) continue;
            LocalDateTime dueAt = doc.getDcAssignedAt().plusMinutes(config.getTimeoutMinutes());
            if (now.isBefore(dueAt)) continue;

            Long from = doc.getCurrentOwnerUserId();
            doc.setCurrentOwnerUserId(receiver.getId());
            documentUserViewRepository.deleteByDocumentIdAndUserId(doc.getId(), receiver.getId());
            doc.setStatus(Status.IN_PROGRESS);
            doc.setDcAssignedAt(null);
            doc.setDcViewedAt(null);
            doc.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(doc);

            String forwardVisibility = doc.getVisibility();
            if (forwardVisibility == null || forwardVisibility.isBlank()) {
                forwardVisibility = "PUBLIC";
            } else {
                forwardVisibility = forwardVisibility.trim().toUpperCase();
            }

            DocumentMovement mv = DocumentMovement.create(
                    doc.getId(),
                    from,
                    receiver.getId(),
                    from,
                    MovementActionType.FORWARD,
                    forwardVisibility
            );
            movementRepository.save(mv);

            auditLogService.logMovement(
                    doc.getId(),
                    from,
                    "AUTO_FORWARD_DC_TIMEOUT",
                    "Auto-forwarded after DC did not view in " + config.getTimeoutMinutes() + " minute(s). Receiver userId=" + receiver.getId()
            );
        }
    }
}
