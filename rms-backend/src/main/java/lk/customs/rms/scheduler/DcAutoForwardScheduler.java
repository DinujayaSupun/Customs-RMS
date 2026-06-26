package lk.customs.rms.scheduler;

import lk.customs.rms.entity.DcAutoForwardConfig;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentMovement;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.MovementActionType;
import lk.customs.rms.enums.RecipientSetReason;
import lk.customs.rms.enums.Status;
import lk.customs.rms.repository.DocumentMovementRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.DocumentUserViewRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.DcAutoForwardConfigService;
import lk.customs.rms.service.DocumentRecipientService;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DcAutoForwardScheduler {

    private static final int AUTO_FORWARD_BATCH_SIZE = 100;
    private static final int AUTO_FORWARD_MAX_PER_RUN = 1000;

    private final DcAutoForwardConfigService dcAutoForwardConfigService;
    private final DocumentRepository documentRepository;
    private final DocumentMovementRepository movementRepository;
    private final DocumentUserViewRepository documentUserViewRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final DocumentRecipientService documentRecipientService;

    public DcAutoForwardScheduler(DcAutoForwardConfigService dcAutoForwardConfigService,
                                  DocumentRepository documentRepository,
                                  DocumentMovementRepository movementRepository,
                                  DocumentUserViewRepository documentUserViewRepository,
                                  UserRepository userRepository,
                                  AuditLogService auditLogService,
                                  DocumentRecipientService documentRecipientService) {
        this.dcAutoForwardConfigService = dcAutoForwardConfigService;
        this.documentRepository = documentRepository;
        this.movementRepository = movementRepository;
        this.documentUserViewRepository = documentUserViewRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.documentRecipientService = documentRecipientService;
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

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffAt = now.minusMinutes(config.getTimeoutMinutes());
        int processed = 0;

        while (processed < AUTO_FORWARD_MAX_PER_RUN) {
            int remaining = AUTO_FORWARD_MAX_PER_RUN - processed;
            var page = PageRequest.of(0, Math.min(AUTO_FORWARD_BATCH_SIZE, remaining));
            List<Document> candidates = documentRepository
                    .findPendingDcAutoForwardCandidates(dcUserIds, cutoffAt, page)
                    .getContent();
            if (candidates.isEmpty()) return;

            for (Document doc : candidates) {
                autoForwardDocument(doc, receiver.getId(), config.getTimeoutMinutes(), now);
                processed += 1;
            }
        }
    }

    private void autoForwardDocument(Document doc, Long receiverUserId, int timeoutMinutes, LocalDateTime now) {
        Long from = doc.getCurrentOwnerUserId();
        Map<String, List<Long>> previousRecipients = documentRecipientService.getActiveRecipientsByType(doc.getId());
        doc.setCurrentOwnerUserId(receiverUserId);
        documentUserViewRepository.deleteByDocumentIdAndUserId(doc.getId(), receiverUserId);
        doc.setStatus(Status.IN_PROGRESS);
        doc.setDcAssignedAt(null);
        doc.setDcViewedAt(null);
        doc.setUpdatedAt(now);
        doc = documentRepository.save(doc);

        String forwardVisibility = doc.getVisibility();
        if (forwardVisibility == null || forwardVisibility.isBlank()) {
            // Fail closed: unknown visibility is treated as PRIVATE, consistent with effectiveVisibility().
            forwardVisibility = "PRIVATE";
        } else {
            forwardVisibility = forwardVisibility.trim().toUpperCase();
        }

        DocumentMovement mv = DocumentMovement.create(
                doc.getId(),
                from,
                receiverUserId,
                from,
                MovementActionType.FORWARD,
                forwardVisibility
        );
        DocumentMovement savedMovement = movementRepository.save(mv);
        documentRecipientService.preserveCopiedRecipientsWithNewTo(
                doc,
                receiverUserId,
                from,
                savedMovement.getId(),
                RecipientSetReason.AUTO_FORWARD
        );
        documentRepository.save(doc);
        Map<String, List<Long>> preservedRecipients = documentRecipientService.getActiveRecipientsByType(doc.getId());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("documentId", doc.getId());
        details.put("oldToUserId", from);
        details.put("newToUserId", receiverUserId);
        details.put("toUserId", receiverUserId);
        details.put("toUserIds", preservedRecipients.getOrDefault("to", List.of()));
        details.put("ccUserIds", preservedRecipients.getOrDefault("cc", List.of()));
        details.put("bccUserIds", preservedRecipients.getOrDefault("bcc", List.of()));
        details.put("preservedCcUserIds", previousRecipients.getOrDefault("cc", List.of()));
        details.put("preservedBccUserIds", previousRecipients.getOrDefault("bcc", List.of()));
        details.put("movementId", savedMovement.getId());
        details.put("activeRecipientSetId", documentRecipientService.activeRecipientSetId(doc.getId()).orElse(null));
        details.put("timeoutMinutes", timeoutMinutes);

        auditLogService.logEventWithDetails(
                "MOVEMENT",
                doc.getId(),
                "AUTO_FORWARD_DC_TIMEOUT",
                from,
                "Auto-forwarded after DC did not view in " + timeoutMinutes + " minute(s). Receiver userId=" + receiverUserId,
                details
        );
    }
}
