package lk.customs.rms.service.impl;

import lk.customs.rms.dto.RecipientSummaryResponse;
import lk.customs.rms.dto.RecipientUserResponse;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentRecipient;
import lk.customs.rms.entity.DocumentRecipientSet;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.enums.RecipientSetReason;
import lk.customs.rms.enums.RecipientType;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.repository.DocumentRecipientRepository;
import lk.customs.rms.repository.DocumentRecipientSetRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.DocumentRecipientService;
import lk.customs.rms.service.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentRecipientServiceImpl implements DocumentRecipientService {

    private final DocumentRecipientSetRepository recipientSetRepository;
    private final DocumentRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    public DocumentRecipientServiceImpl(DocumentRecipientSetRepository recipientSetRepository,
                                        DocumentRecipientRepository recipientRepository,
                                        UserRepository userRepository,
                                        PermissionService permissionService) {
        this.recipientSetRepository = recipientSetRepository;
        this.recipientRepository = recipientRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
    }

    @Override
    @Transactional
    public void createInitialSet(Document document, Long actorUserId, Long movementId) {
        createReplacementSet(document, document.getCurrentOwnerUserId(), List.of(), List.of(), actorUserId, movementId, RecipientSetReason.CREATE);
    }

    @Override
    @Transactional
    public void createForwardSet(Document document, Long toUserId, List<Long> ccUserIds, List<Long> bccUserIds,
                                 Long actorUserId, Long movementId) {
        createReplacementSet(document, toUserId, ccUserIds, bccUserIds, actorUserId, movementId, RecipientSetReason.FORWARD);
    }

    @Override
    @Transactional
    public void createOwnerOnlySet(Document document, Long toUserId, Long actorUserId, Long movementId, RecipientSetReason reason) {
        document.setCurrentOwnerUserId(toUserId);
        createReplacementSet(document, toUserId, List.of(), List.of(), actorUserId, movementId, reason);
    }

    @Override
    @Transactional
    public void updateCopiedRecipients(Document document, List<Long> ccUserIds, List<Long> bccUserIds, Long actorUserId) {
        if (!canManageRecipients(document, actorUserId)) {
            throw new BadRequestException("You are not allowed to manage document recipients.");
        }
        createReplacementSet(document, document.getCurrentOwnerUserId(), ccUserIds, bccUserIds, actorUserId, null, RecipientSetReason.MANUAL_UPDATE);
    }

    @Override
    @Transactional
    public void restorePreviousSet(Document document, Long actorUserId, Long movementId, RecipientSetReason reason) {
        List<DocumentRecipientSet> sets = recipientSetRepository.findByDocumentIdOrderByCreatedAtDescIdDesc(document.getId());
        if (sets.size() < 2) {
            createReplacementSet(document, document.getCurrentOwnerUserId(), List.of(), List.of(), actorUserId, movementId, reason);
            return;
        }

        DocumentRecipientSet previousSet = sets.stream()
                .filter(set -> !set.isActive())
                .findFirst()
                .orElse(sets.get(1));
        List<DocumentRecipient> previousRecipients = recipientRepository.findByRecipientSetIdOrderByRecipientTypeAscUserIdAsc(previousSet.getId());
        Long toUserId = userIds(previousRecipients, RecipientType.TO).stream().findFirst()
                .orElseThrow(() -> new BadRequestException("Previous recipient set has no TO user."));
        document.setCurrentOwnerUserId(toUserId);

        createReplacementSet(
                document,
                toUserId,
                userIds(previousRecipients, RecipientType.CC),
                userIds(previousRecipients, RecipientType.BCC),
                actorUserId,
                movementId,
                reason
        );
    }

    @Override
    @Transactional
    public void restorePreviousSetForTo(Document document, Long toUserId, Long actorUserId, Long movementId, RecipientSetReason reason) {
        if (toUserId == null) {
            throw new BadRequestException("To user is required.");
        }

        List<DocumentRecipientSet> sets = recipientSetRepository.findByDocumentIdOrderByCreatedAtDescIdDesc(document.getId());
        for (DocumentRecipientSet set : sets) {
            if (set.isActive()) continue;

            List<DocumentRecipient> recipients = recipientRepository.findByRecipientSetIdOrderByRecipientTypeAscUserIdAsc(set.getId());
            boolean matchesTo = userIds(recipients, RecipientType.TO).stream().anyMatch(toUserId::equals);
            if (!matchesTo) continue;

            document.setCurrentOwnerUserId(toUserId);
            createReplacementSet(
                    document,
                    toUserId,
                    userIds(recipients, RecipientType.CC),
                    userIds(recipients, RecipientType.BCC),
                    actorUserId,
                    movementId,
                    reason
            );
            return;
        }

        document.setCurrentOwnerUserId(toUserId);
        createReplacementSet(document, toUserId, List.of(), List.of(), actorUserId, movementId, reason);
    }

    @Override
    @Transactional
    public void preserveCopiedRecipientsWithNewTo(Document document, Long newToUserId, Long actorUserId,
                                                  Long movementId, RecipientSetReason reason) {
        List<DocumentRecipient> currentRecipients = activeRecipients(document.getId());
        document.setCurrentOwnerUserId(newToUserId);
        createReplacementSet(
                document,
                newToUserId,
                userIds(currentRecipients, RecipientType.CC),
                userIds(currentRecipients, RecipientType.BCC),
                actorUserId,
                movementId,
                reason
        );
    }

    @Override
    public Optional<RecipientType> activeRecipientType(Long documentId, Long userId) {
        if (documentId == null || userId == null) return Optional.empty();
        return recipientRepository.findActiveForDocumentAndUser(documentId, userId)
                .stream()
                .map(DocumentRecipient::getRecipientType)
                .findFirst();
    }

    @Override
    public boolean isActiveRecipient(Long documentId, Long userId) {
        return activeRecipientType(documentId, userId).isPresent();
    }

    @Override
    public boolean canViewAsCopiedRecipient(Document document, Long userId) {
        Optional<RecipientType> type = activeRecipientType(document.getId(), userId);
        return type.filter(recipientType -> switch (recipientType) {
            case TO -> true;
            case CC -> permissionService.hasPermission(userId, AppPermission.CC_VIEW_DOCUMENT);
            case BCC -> permissionService.hasPermission(userId, AppPermission.BCC_VIEW_DOCUMENT);
        }).isPresent();
    }

    @Override
    public boolean canViewAttachments(Document document, Long userId) {
        if (document.getCurrentOwnerUserId().equals(userId)) return true;
        if (permissionService.hasPermission(userId, AppPermission.VIEW_ALL_HISTORY)) return true;
        return activeRecipientType(document.getId(), userId)
                .filter(type -> type == RecipientType.CC
                        ? permissionService.hasPermission(userId, AppPermission.CC_VIEW_ATTACHMENTS)
                        : type == RecipientType.BCC && permissionService.hasPermission(userId, AppPermission.BCC_VIEW_ATTACHMENTS))
                .isPresent();
    }

    @Override
    public boolean canUploadAttachment(Document document, Long userId) {
        if (document.getCurrentOwnerUserId().equals(userId)) return permissionService.hasPermission(userId, AppPermission.UPLOAD_ATTACHMENT);
        return activeRecipientType(document.getId(), userId)
                .filter(type -> type == RecipientType.CC
                        ? permissionService.hasPermission(userId, AppPermission.CC_UPLOAD_ATTACHMENTS)
                        : type == RecipientType.BCC && permissionService.hasPermission(userId, AppPermission.BCC_UPLOAD_ATTACHMENTS))
                .isPresent()
                && permissionService.hasPermission(userId, AppPermission.UPLOAD_ATTACHMENT);
    }

    @Override
    public boolean canDeleteOwnAttachment(Document document, Long userId) {
        if (document.getCurrentOwnerUserId().equals(userId)) return permissionService.hasPermission(userId, AppPermission.DELETE_ATTACHMENT);
        return activeRecipientType(document.getId(), userId)
                .filter(type -> type == RecipientType.CC
                        ? permissionService.hasPermission(userId, AppPermission.CC_DELETE_OWN_ATTACHMENTS)
                        : type == RecipientType.BCC && permissionService.hasPermission(userId, AppPermission.BCC_DELETE_OWN_ATTACHMENTS))
                .isPresent()
                && permissionService.hasPermission(userId, AppPermission.DELETE_ATTACHMENT);
    }

    @Override
    public boolean canViewTimeline(Document document, Long userId) {
        if (document.getCurrentOwnerUserId().equals(userId)) return true;
        if (permissionService.hasPermission(userId, AppPermission.VIEW_ALL_HISTORY)) return true;
        return activeRecipientType(document.getId(), userId)
                .filter(type -> type == RecipientType.CC
                        ? permissionService.hasPermission(userId, AppPermission.CC_VIEW_TIMELINE)
                        : type == RecipientType.BCC && permissionService.hasPermission(userId, AppPermission.BCC_VIEW_TIMELINE))
                .isPresent();
    }

    @Override
    public boolean canViewMinutes(Document document, Long userId) {
        if (document.getCurrentOwnerUserId().equals(userId)) return true;
        if (permissionService.hasPermission(userId, AppPermission.VIEW_REMARKS_WHEN_NOT_REPORT_AT)) return true;
        return activeRecipientType(document.getId(), userId)
                .filter(type -> type == RecipientType.CC
                        ? permissionService.hasPermission(userId, AppPermission.CC_VIEW_MINUTES)
                        : type == RecipientType.BCC && permissionService.hasPermission(userId, AppPermission.BCC_VIEW_MINUTES))
                .isPresent();
    }

    @Override
    public boolean canManageRecipients(Document document, Long userId) {
        if (document == null || userId == null) return false;
        if (permissionService.hasPermission(userId, AppPermission.MANAGE_ANY_DOCUMENT_RECIPIENTS)) return true;
        return document.getCurrentOwnerUserId().equals(userId)
                && permissionService.hasPermission(userId, AppPermission.MANAGE_DOCUMENT_RECIPIENTS);
    }

    @Override
    public RecipientSummaryResponse summaryForViewer(Document document, Long viewerUserId) {
        List<DocumentRecipient> recipients = activeRecipients(document.getId());
        if (recipients.isEmpty()) {
            DocumentRecipient fallback = new DocumentRecipient();
            fallback.setDocumentId(document.getId());
            fallback.setUserId(document.getCurrentOwnerUserId());
            fallback.setRecipientType(RecipientType.TO);
            recipients = List.of(fallback);
        }

        Set<Long> userIds = recipients.stream().map(DocumentRecipient::getUserId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> usersById = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        boolean canViewHidden = permissionService.hasPermission(viewerUserId, AppPermission.VIEW_HIDDEN_RECIPIENTS);
        boolean viewerIsBcc = recipients.stream().anyMatch(r -> r.getRecipientType() == RecipientType.BCC && r.getUserId().equals(viewerUserId));

        List<RecipientUserResponse> to = mapUsers(recipients, RecipientType.TO, usersById, viewerUserId, false, false);
        List<RecipientUserResponse> cc = mapUsers(recipients, RecipientType.CC, usersById, viewerUserId, false, false);
        List<RecipientUserResponse> bcc = canViewHidden
                ? mapUsers(recipients, RecipientType.BCC, usersById, viewerUserId, false, false)
                : viewerIsBcc
                    ? mapUsers(recipients, RecipientType.BCC, usersById, viewerUserId, true, true)
                    : List.of();

        return RecipientSummaryResponse.builder()
                .to(to)
                .cc(cc)
                .bcc(bcc)
                .compactText(compactText(to, cc, bcc))
                .build();
    }

    @Override
    public RecipientSummaryResponse summaryForMovement(Document document, Long movementId, Long viewerUserId) {
        if (document == null || movementId == null) {
            return emptySummary();
        }

        return recipientSetRepository.findFirstByDocumentIdAndMovementIdOrderByCreatedAtDescIdDesc(document.getId(), movementId)
                .map(set -> summaryFromRecipients(
                        recipientRepository.findByRecipientSetIdOrderByRecipientTypeAscUserIdAsc(set.getId()),
                        viewerUserId
                ))
                .orElseGet(this::emptySummary);
    }

    @Override
    public RecipientSummaryResponse summaryForSentMovement(Document document, Long movementId, Long viewerUserId) {
        if (document == null || movementId == null) {
            return emptySummary();
        }

        return recipientSetRepository.findFirstByDocumentIdAndMovementIdOrderByCreatedAtDescIdDesc(document.getId(), movementId)
                .map(set -> summaryFromRecipients(
                        recipientRepository.findByRecipientSetIdOrderByRecipientTypeAscUserIdAsc(set.getId()),
                        viewerUserId,
                        set.getCreatedByUserId() != null && set.getCreatedByUserId().equals(viewerUserId)
                ))
                .orElseGet(this::emptySummary);
    }

    @Override
    public List<DocumentRecipient> activeRecipients(Long documentId) {
        return recipientSetRepository.findFirstByDocumentIdAndActiveTrueOrderByCreatedAtDescIdDesc(documentId)
                .map(set -> recipientRepository.findByRecipientSetIdOrderByRecipientTypeAscUserIdAsc(set.getId()))
                .orElse(List.of());
    }

    @Override
    public Optional<Long> activeRecipientSetId(Long documentId) {
        return recipientSetRepository.findFirstByDocumentIdAndActiveTrueOrderByCreatedAtDescIdDesc(documentId)
                .map(DocumentRecipientSet::getId);
    }

    @Override
    public java.util.Map<String, java.util.List<Long>> getActiveRecipientsByType(Long documentId) {
        List<DocumentRecipient> recipients = activeRecipients(documentId);
        return Map.of(
                "to", userIds(recipients, RecipientType.TO),
                "cc", userIds(recipients, RecipientType.CC),
                "bcc", userIds(recipients, RecipientType.BCC)
        );
    }

    private void createReplacementSet(Document document, Long toUserId, List<Long> ccUserIds, List<Long> bccUserIds,
                                      Long actorUserId, Long movementId, RecipientSetReason reason) {
        validateRecipients(toUserId, ccUserIds, bccUserIds);
        deactivateActiveSets(document.getId());

        DocumentRecipientSet set = new DocumentRecipientSet();
        set.setDocumentId(document.getId());
        set.setMovementId(movementId);
        set.setCreatedByUserId(actorUserId);
        set.setCreatedAt(LocalDateTime.now());
        set.setActive(true);
        set.setReason(reason);
        DocumentRecipientSet savedSet = recipientSetRepository.save(set);

        List<DocumentRecipient> recipients = new ArrayList<>();
        recipients.add(recipient(savedSet, toUserId, RecipientType.TO, actorUserId));
        for (Long ccUserId : normalizeIds(ccUserIds)) {
            recipients.add(recipient(savedSet, ccUserId, RecipientType.CC, actorUserId));
        }
        for (Long bccUserId : normalizeIds(bccUserIds)) {
            recipients.add(recipient(savedSet, bccUserId, RecipientType.BCC, actorUserId));
        }
        recipientRepository.saveAll(recipients);
    }

    private RecipientSummaryResponse summaryFromRecipients(List<DocumentRecipient> recipients, Long viewerUserId) {
        return summaryFromRecipients(recipients, viewerUserId, false);
    }

    private RecipientSummaryResponse summaryFromRecipients(List<DocumentRecipient> recipients, Long viewerUserId, boolean revealHidden) {
        Set<Long> userIds = recipients.stream().map(DocumentRecipient::getUserId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds)
                    .stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));

        boolean canViewHidden = revealHidden || permissionService.hasPermission(viewerUserId, AppPermission.VIEW_HIDDEN_RECIPIENTS);
        boolean viewerIsBcc = recipients.stream().anyMatch(r -> r.getRecipientType() == RecipientType.BCC && r.getUserId().equals(viewerUserId));

        List<RecipientUserResponse> to = mapUsers(recipients, RecipientType.TO, usersById, viewerUserId, false, false);
        List<RecipientUserResponse> cc = mapUsers(recipients, RecipientType.CC, usersById, viewerUserId, false, false);
        List<RecipientUserResponse> bcc = canViewHidden
                ? mapUsers(recipients, RecipientType.BCC, usersById, viewerUserId, false, false)
                : viewerIsBcc
                    ? mapUsers(recipients, RecipientType.BCC, usersById, viewerUserId, true, true)
                    : List.of();

        return RecipientSummaryResponse.builder()
                .to(to)
                .cc(cc)
                .bcc(bcc)
                .compactText(compactText(to, cc, bcc))
                .build();
    }

    private RecipientSummaryResponse emptySummary() {
        return RecipientSummaryResponse.builder()
                .to(List.of())
                .cc(List.of())
                .bcc(List.of())
                .compactText("")
                .build();
    }

    private void deactivateActiveSets(Long documentId) {
        List<DocumentRecipientSet> activeSets = recipientSetRepository.findByDocumentIdAndActiveTrue(documentId);
        for (DocumentRecipientSet activeSet : activeSets) {
            activeSet.setActive(false);
        }
        recipientSetRepository.saveAll(activeSets);
    }

    private void validateRecipients(Long toUserId, List<Long> ccUserIds, List<Long> bccUserIds) {
        if (toUserId == null) throw new BadRequestException("To user is required.");

        Set<Long> seen = new HashSet<>();
        List<Long> all = new ArrayList<>();
        all.add(toUserId);
        all.addAll(normalizeIds(ccUserIds));
        all.addAll(normalizeIds(bccUserIds));

        for (Long userId : all) {
            if (userId == null) throw new BadRequestException("Recipient user is required.");
            if (!seen.add(userId)) throw new BadRequestException("A user cannot be added more than once across To, CC, and BCC.");
        }

        Map<Long, User> users = userRepository.findAllById(all)
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        for (Long userId : all) {
            User user = users.get(userId);
            if (user == null) throw new BadRequestException("Recipient user not found: " + userId);
            if (!Boolean.TRUE.equals(user.getIsActive())) throw new BadRequestException("Recipient user is inactive: " + userId);
        }
    }

    private DocumentRecipient recipient(DocumentRecipientSet set, Long userId, RecipientType type, Long actorUserId) {
        DocumentRecipient recipient = new DocumentRecipient();
        recipient.setRecipientSetId(set.getId());
        recipient.setDocumentId(set.getDocumentId());
        recipient.setUserId(userId);
        recipient.setRecipientType(type);
        recipient.setAddedByUserId(actorUserId);
        recipient.setAddedAt(set.getCreatedAt());
        return recipient;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream().filter(id -> id != null).distinct().toList();
    }

    private List<Long> userIds(List<DocumentRecipient> recipients, RecipientType type) {
        return recipients.stream()
                .filter(r -> r.getRecipientType() == type)
                .map(DocumentRecipient::getUserId)
                .toList();
    }

    private List<RecipientUserResponse> mapUsers(List<DocumentRecipient> recipients, RecipientType type,
                                                 Map<Long, User> usersById, Long viewerUserId,
                                                 boolean hideNames, boolean includeViewerOnly) {
        return recipients.stream()
                .filter(r -> r.getRecipientType() == type)
                .filter(r -> !includeViewerOnly || r.getUserId().equals(viewerUserId))
                .map(r -> toRecipientUser(r.getUserId(), usersById.get(r.getUserId()), viewerUserId, hideNames))
                .toList();
    }

    private RecipientUserResponse toRecipientUser(Long userId, User user, Long viewerUserId, boolean hideName) {
        boolean currentUser = userId != null && userId.equals(viewerUserId);
        return RecipientUserResponse.builder()
                .userId(hideName && !currentUser ? null : userId)
                .name(currentUser ? "you" : hideName ? "hidden" : user == null ? "User ID " + userId : user.getFullName())
                .role(hideName ? null : roleName(user))
                .currentUser(currentUser)
                .build();
    }

    private String compactText(List<RecipientUserResponse> to, List<RecipientUserResponse> cc, List<RecipientUserResponse> bcc) {
        List<String> parts = new ArrayList<>();
        if (!to.isEmpty()) parts.add("To: " + compactNames(to));
        if (!cc.isEmpty()) parts.add("CC: " + compactNames(cc));
        if (!bcc.isEmpty()) parts.add("BCC: " + compactNames(bcc));
        return String.join(" • ", parts);
    }

    private String compactNames(List<RecipientUserResponse> users) {
        if (users.isEmpty()) return "-";
        if (users.size() == 1) return users.get(0).getName();
        return users.get(0).getName() + " +" + (users.size() - 1);
    }

    private String roleName(User user) {
        return user == null || user.getRole() == null ? null : user.getRole().getRoleName();
    }
}
