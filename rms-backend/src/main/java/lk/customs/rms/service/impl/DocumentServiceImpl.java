package lk.customs.rms.service.impl;

import lk.customs.rms.dto.*;
import lk.customs.rms.dto.RecipientSummaryResponse;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentAttachment;
import lk.customs.rms.entity.DocumentMovement;
import lk.customs.rms.entity.DocumentRemark;
import lk.customs.rms.entity.DocumentUserView;
import lk.customs.rms.entity.AuditLog;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.enums.MovementActionType;
import lk.customs.rms.enums.Priority;
import lk.customs.rms.enums.RecipientSetReason;
import lk.customs.rms.enums.RecipientType;
import lk.customs.rms.enums.Status;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.exception.ResourceNotFoundException;
import lk.customs.rms.repository.DocumentMovementRepository;
import lk.customs.rms.repository.DocumentAttachmentRepository;
import lk.customs.rms.repository.DocumentRemarkRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.DocumentUserViewRepository;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.repository.RecipientGroupMemberRepository;
import lk.customs.rms.repository.RecipientGroupRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.entity.RecipientGroup;
import lk.customs.rms.entity.RecipientGroupMember;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.DcAutoForwardConfigService;
import lk.customs.rms.service.DocumentRecipientService;
import lk.customs.rms.service.DocumentService;
import lk.customs.rms.service.PermissionService;
import lk.customs.rms.service.RealtimeNotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * ==========================================================
 * FILE: DocumentServiceImpl.java
 * DC_ROLE_NAME: the role whose auto-forward timeout is tracked.
 *
 * PURPOSE:
 *   Implements Sri Lanka Customs Document Workflow rules.
 *
 * IMPORTANT BUSINESS RULES:
 *   1) Ownership rule:
 *      - Only CURRENT OWNER can forward/return/add remarks.
 *
 *   2) Permission-controlled decisions:
 *      - Approve / Reject / Issue / Reopen depend on the assigned role permissions.
 *      - User must still be the current owner to do those actions.
 *
 *   3) FINAL STATE LOCK (critical):
 *      - Once ISSUED or REJECTED => NO further workflow actions allowed.
 *      - If APPROVED => ONLY ISSUE is allowed (no forward/return/approve/reject).
 *      - ISSUE allowed ONLY when status == APPROVED.
 *
 *   4) REOPEN (NEW):
 *      - Only users with reopen permission can REOPEN an APPROVED or REJECTED document.
 *      - Not allowed if ISSUED (final).
 *      - Requires a reason (remarkText must not be empty).
 *      - Sets status back to IN_PROGRESS and clears completedAt.
 *      - Logs Movement(REOPEN) + Audit log + Remark.
 *
 *   5) Remarks:
 *      - Can be added standalone OR during workflow action.
 *      - Always saved BEFORE ownership changes.
 * ==========================================================
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    private static final String DC_ROLE_NAME = "DC";

    private record UndoSendState(
            boolean canUndo,
            String status,
            LocalDateTime expiresAt,
            boolean receiverOpened,
            String actionType,
            boolean requiresReason,
            boolean showExpiredInfo
    ) {}

    private record RecipientCapabilities(
            String recipientType,
            RecipientSummaryResponse recipientSummary,
            boolean canManageRecipients,
            boolean canWorkflow,
            boolean canViewAttachments,
            boolean canUploadAttachment,
            boolean canDeleteOwnAttachment,
            boolean canViewTimeline,
            boolean canViewMinutes
    ) {}

    private final DocumentRepository documentRepository;
    private final DocumentAttachmentRepository attachmentRepository;
    private final DocumentMovementRepository movementRepository;
    private final DocumentRemarkRepository remarkRepository;
    private final DocumentUserViewRepository documentUserViewRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final PermissionService permissionService;
    private final RealtimeNotificationService realtimeNotificationService;
    private final DcAutoForwardConfigService dcAutoForwardConfigService;
    private final DocumentRecipientService documentRecipientService;
    private final RecipientGroupRepository recipientGroupRepository;
    private final RecipientGroupMemberRepository recipientGroupMemberRepository;

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            DocumentAttachmentRepository attachmentRepository,
            DocumentMovementRepository movementRepository,
            DocumentRemarkRepository remarkRepository,
            DocumentUserViewRepository documentUserViewRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            AuditLogService auditLogService,
                PermissionService permissionService,
                RealtimeNotificationService realtimeNotificationService,
                DcAutoForwardConfigService dcAutoForwardConfigService,
                DocumentRecipientService documentRecipientService,
                RecipientGroupRepository recipientGroupRepository,
                RecipientGroupMemberRepository recipientGroupMemberRepository
    ) {
        this.documentRepository = documentRepository;
        this.attachmentRepository = attachmentRepository;
        this.movementRepository = movementRepository;
        this.remarkRepository = remarkRepository;
        this.documentUserViewRepository = documentUserViewRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
        this.permissionService = permissionService;
        this.realtimeNotificationService = realtimeNotificationService;
        this.dcAutoForwardConfigService = dcAutoForwardConfigService;
        this.documentRecipientService = documentRecipientService;
        this.recipientGroupRepository = recipientGroupRepository;
        this.recipientGroupMemberRepository = recipientGroupMemberRepository;
    }

    // ==========================================================
    // DOCUMENT CRUD
    // ==========================================================

    @Transactional
    @Override
    public DocumentResponse createDocument(CreateDocumentRequest request, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.CREATE_DOCUMENT, "You are not allowed to create documents.");

        // Unique constraint check (business)
        if (documentRepository.existsByRefNoAndDeletedFalse(request.getRefNo())) {
            throw new BadRequestException("Ref No already exists: " + request.getRefNo());
        }

        User createdBy = requireUser(actorUserId);
        User owner = createdBy;

        Document doc = new Document();
        doc.setRefNo(request.getRefNo());
        doc.setTitle(request.getTitle());
        doc.setReceivedDate(request.getReceivedDate());
        doc.setCompanyName(request.getCompanyName());
        doc.setPriority(request.getPriority());
        doc.setDocumentType(request.getDocumentType());
        doc.setVisibility("PRIVATE");

        // Document starts as PENDING
        doc.setStatus(Status.PENDING);

        doc.setCreatedByUserId(createdBy.getId());
        doc.setCurrentOwnerUserId(createdBy.getId());
        LocalDateTime now = LocalDateTime.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        doc.setDeleted(false);

        Document saved = documentRepository.save(doc);

        // Movement: CREATE (file is born)
        DocumentMovement mv = DocumentMovement.create(
                saved.getId(),
                null,
                saved.getCurrentOwnerUserId(),
                createdBy.getId(),
                MovementActionType.CREATE
        );
        DocumentMovement savedMovement = movementRepository.save(mv);
        documentRecipientService.createInitialSet(saved, createdBy.getId(), savedMovement.getId());

        auditLogService.logDocumentCreate(saved.getId(), createdBy.getId(), "Document created");

        return DocumentResponse.from(withRecipientCapabilities(DocumentResponse.mapping(saved)
                .createdByName(createdBy.getFullName())
                .ownerName(owner.getFullName())
                .canDelete(canDeleteDocument(saved, actorUserId)),
                saved,
                actorUserId)
                .build());
    }

    @Transactional(readOnly = true)
    @Override
    public Page<DocumentResponse> getDocuments(int page, int size, String search, String status, String priority,
                                               String receivedFrom, String receivedTo, String sort, Long actorUserId) {
        var pageable = PageRequest.of(page, size, resolveDocumentSort(sort));

        boolean canViewAll = permissionService.hasPermission(actorUserId, AppPermission.VIEW_ALL_DOCUMENTS);
        boolean canViewPublic = permissionService.hasPermission(actorUserId, AppPermission.VIEW_PUBLIC_DOCUMENT);
        boolean canViewPrivate = permissionService.hasPermission(actorUserId, AppPermission.VIEW_PRIVATE_DOCUMENT);
        boolean canViewOwnCreated = permissionService.hasPermission(actorUserId, AppPermission.VIEW_OWN_CREATED_DOCUMENTS);
        Status statusFilter = parseStatus(status);
        Priority priorityFilter = parsePriority(priority);
        LocalDate receivedFromFilter = parseDate(receivedFrom);
        LocalDate receivedToFilter = parseDate(receivedTo);
        String normalizedSearch = normalizeSearch(search);

        Page<Document> docs;
        if (canViewAll) {
            docs = documentRepository.searchAllNotDeletedFiltered(
                normalizedSearch,
                statusFilter,
                priorityFilter,
                receivedFromFilter,
                receivedToFilter,
                pageable
            );
        } else {
            docs = documentRepository.searchAccessibleNotDeletedFiltered(
                normalizedSearch,
                statusFilter,
                priorityFilter,
                receivedFromFilter,
                receivedToFilter,
                actorUserId,
                canViewPublic,
                canViewPrivate,
                canViewOwnCreated,
                MovementActionType.FORWARD,
                pageable
            );
        }

        return toDocumentResponsePage(docs, actorUserId);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<DocumentResponse> getMyInboxDocuments(int page, int size, String search, String status, String priority,
                                                      String sort, RecipientType recipientType, Long actorUserId) {
        var pageable = PageRequest.of(page, size, resolveDocumentSort(sort));

        Page<Document> docs = documentRepository.findInboxAccessibleActiveFiltered(
            actorUserId,
            List.of(RecipientType.CC, RecipientType.BCC),
            recipientType,
            Status.ISSUED,
            normalizeSearch(search),
            parseStatus(status),
            parsePriority(priority),
            pageable
        );
        return toDocumentResponsePage(docs, actorUserId);
    }

    private Page<DocumentResponse> toDocumentResponsePage(Page<Document> docs, Long actorUserId) {
        List<Long> docIds = docs.getContent().stream().map(Document::getId).toList();
        Set<Long> viewedDocIds = docIds.isEmpty()
            ? Set.of()
            : new HashSet<>(documentUserViewRepository.findViewedDocumentIdsByUser(actorUserId, docIds));
        Map<Long, String> mainAttachmentTypes = docIds.isEmpty()
            ? Map.of()
            : attachmentRepository
                .findByDocumentIdInAndDeletedFalseAndIsLatestTrue(docIds)
                .stream()
                .collect(Collectors.toMap(
                    DocumentAttachment::getDocumentId,
                    a -> resolveAttachmentTypeFromFileName(a.getFileName()),
                    (first, second) -> first
                ));
        Map<Long, DocumentRemark> latestRemarks = docIds.isEmpty()
            ? Map.of()
            : remarkRepository.findLatestByDocumentIdsWithUser(docIds)
                .stream()
                .collect(Collectors.toMap(
                    DocumentRemark::getDocumentId,
                    remark -> remark,
                    (first, second) -> first
                ));
        // A viewer can be CC'd purely through group membership (no toUserId movement of their own),
        // so the inbound-movement lookup below also needs the groups they belong to on this page.
        Set<Long> groupIdsOnPage = docs.getContent().stream()
            .map(Document::getCurrentOwnerGroupId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        Set<Long> actorMemberGroupIds = groupIdsOnPage.isEmpty()
            ? Set.of()
            : new HashSet<>(recipientGroupMemberRepository.findMemberGroupIds(actorUserId, groupIdsOnPage));

        Map<Long, DocumentMovement> latestInboundByDoc = docIds.isEmpty()
            ? Map.of()
            : movementRepository.findLatestInboundByActorAndDocumentIds(
                    actorUserId,
                    docIds,
                    actorMemberGroupIds,
                    List.of(MovementActionType.CREATE, MovementActionType.FORWARD, MovementActionType.RETURN, MovementActionType.UNDO_SEND)
                )
                .stream()
                .collect(Collectors.toMap(
                    DocumentMovement::getDocumentId,
                    movement -> movement,
                    (first, second) -> first
                ));
        Set<Long> userIds = new HashSet<>();
        docs.getContent().forEach(d -> {
            if (d.getCreatedByUserId() != null) userIds.add(d.getCreatedByUserId());
            if (d.getCurrentOwnerUserId() != null) userIds.add(d.getCurrentOwnerUserId());
        });
        latestInboundByDoc.values().forEach(m -> {
            Long senderUserId = resolveInboxSenderUserId(m);
            if (senderUserId != null) userIds.add(senderUserId);
            if (m.getActionByUserId() != null) userIds.add(m.getActionByUserId());
            if (m.getFromUserId() != null) userIds.add(m.getFromUserId());
        });
        Map<Long, User> usersById = userIds.isEmpty()
            ? Map.of()
            : userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        boolean canDeleteAnyDocument = permissionService.hasPermission(actorUserId, AppPermission.DELETE_ANY_DOCUMENT);

        // Batch-load recipient data for the whole page to avoid N+1 queries.
        Map<Long, RecipientSummaryResponse> recipientSummaries = docIds.isEmpty()
            ? Map.of()
            : documentRecipientService.summaryBatchForViewer(docIds, actorUserId);
        Map<Long, Optional<RecipientType>> recipientTypes = docIds.isEmpty()
            ? Map.of()
            : documentRecipientService.activeRecipientTypesForUser(docIds, actorUserId);
        Set<AppPermission> actorPermissions = docIds.isEmpty()
            ? java.util.EnumSet.noneOf(AppPermission.class)
            : permissionService.getPermissionsForUser(actorUserId);

        // A group admin can act on any document their group holds, even when they are not the
        // current_owner_user_id anchor. Batch this (one query for the whole page) instead of a
        // per-row isGroupAdmin lookup. groupIdsOnPage was already computed above for the inbound-
        // movement lookup.
        Set<Long> adminGroupIds = groupIdsOnPage.isEmpty()
            ? Set.of()
            : new HashSet<>(recipientGroupMemberRepository.findAdminGroupIds(actorUserId, groupIdsOnPage));

        return docs.map(d -> {
            User createdBy = usersById.get(d.getCreatedByUserId());
            User owner = usersById.get(d.getCurrentOwnerUserId());
            String createdByName = createdBy == null ? null : createdBy.getFullName();
            String ownerName = owner == null ? null : owner.getFullName();
            boolean viewedByMe = viewedDocIds.contains(d.getId());
            boolean isOwnerRow = (d.getCurrentOwnerUserId() != null && d.getCurrentOwnerUserId().equals(actorUserId))
                    || (d.getCurrentOwnerGroupId() != null && adminGroupIds.contains(d.getCurrentOwnerGroupId()));
            RecipientType recipientType = recipientTypes.getOrDefault(d.getId(), Optional.empty())
                    .orElse(isOwnerRow ? RecipientType.TO : null);
            // Minute visibility from already-batched recipient type + permissions (avoids a per-row query).
            boolean canViewRemarksRow = isOwnerRow
                    || actorPermissions.contains(AppPermission.VIEW_REMARKS_WHEN_NOT_REPORT_AT)
                    || (recipientType == RecipientType.CC && actorPermissions.contains(AppPermission.CC_VIEW_MINUTES))
                    || (recipientType == RecipientType.BCC && actorPermissions.contains(AppPermission.BCC_VIEW_MINUTES));
            DocumentRemark latestRemark = canViewRemarksRow ? latestRemarks.get(d.getId()) : null;
            String latestRemarkPreview = latestRemark == null ? null : toRemarkPreview(latestRemark);
            DocumentMovement latestInbound = latestInboundByDoc.get(d.getId());
            Long inboxSenderUserId = resolveInboxSenderUserId(latestInbound);
            User inboxSender = inboxSenderUserId == null ? null : usersById.get(inboxSenderUserId);
            boolean undoInboxMovement = latestInbound != null && latestInbound.getActionType() == MovementActionType.UNDO_SEND;
            // Inbox rows show undo notices as read-only status, not as a new action the receiver can undo again.
            User undoActor = undoInboxMovement && latestInbound.getActionByUserId() != null
                ? usersById.get(latestInbound.getActionByUserId())
                : null;
            User undoFrom = undoInboxMovement && latestInbound.getFromUserId() != null
                ? usersById.get(latestInbound.getFromUserId())
                : null;
            RecipientSummaryResponse recipientSummary = recipientSummaries.get(d.getId());

            return DocumentResponse.from(withPreloadedRecipientCapabilities(DocumentResponse.mapping(d)
                .createdByName(createdByName)
                .ownerName(ownerName)
                .mainAttachmentType(mainAttachmentTypes.get(d.getId()))
                .latestRemarkPreview(latestRemarkPreview)
                .viewedByMe(viewedByMe)
                .inboxReceivedAt(latestInbound == null ? null : latestInbound.getActionAt())
                .inboxSenderUserId(inboxSenderUserId)
                .inboxSenderName(inboxSender == null ? null : inboxSender.getFullName())
                .inboxSenderRole(roleName(inboxSender))
                .latestRemarkByUserId(latestRemark == null ? null : latestRemark.getRemarkedByUserId())
                .latestRemarkByName(latestRemark == null || latestRemark.getRemarkedBy() == null ? null : latestRemark.getRemarkedBy().getFullName())
                .latestRemarkByRole(latestRemark == null ? null : roleName(latestRemark.getRemarkedBy()))
                .latestRemarkTextPreview(latestRemark == null ? null : toRemarkPreview(latestRemark.getRemarkText()))
                .latestRemarkText(latestRemark == null ? null : latestRemark.getRemarkText())
                .latestRemarkAt(latestRemark == null ? null : latestRemark.getRemarkedAt())
                .canUndoSend(false)
                .undoSendStatus(undoInboxMovement ? "UNDONE" : null)
                .undoSendReceiverOpened(false)
                .undoSendActionType(undoInboxMovement ? "UNDO_SEND" : null)
                .undoSendRequiresReason(false)
                .undoSendShowExpiredInfo(false)
                .undoSendByUserId(undoInboxMovement ? latestInbound.getActionByUserId() : null)
                .undoSendByName(undoActor == null ? null : undoActor.getFullName())
                .undoSendByRole(roleName(undoActor))
                .undoSendFromUserId(undoInboxMovement ? latestInbound.getFromUserId() : null)
                .undoSendFromName(undoFrom == null ? null : undoFrom.getFullName())
                .undoSendFromRole(roleName(undoFrom))
                .canDelete(canDeleteDocument(d, actorUserId, canDeleteAnyDocument, actorPermissions.contains(AppPermission.DELETE_DOCUMENT))),
                isOwnerRow, recipientType, recipientSummary, actorPermissions)
                .build());
        });
    }

    @Transactional(readOnly = true)
    @Override
    public Page<SentMessageResponse> getSentMessages(int page, int size, String search, String status, String priority, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.VIEW_SENT_MESSAGES, "You are not allowed to view sent messages.");

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("actionAt"), Sort.Order.desc("id")));
        Page<DocumentMovement> movementPage = movementRepository.findSentPageForActor(
            actorUserId,
            List.of(MovementActionType.FORWARD, MovementActionType.RETURN),
            MovementActionType.UNDO_SEND,
            normalizeSearch(search),
            parseStatus(status),
            parsePriority(priority),
            pageable
        );

        List<DocumentMovement> pageMovements = movementPage.getContent();
        List<Long> docIds = pageMovements.stream().map(DocumentMovement::getDocumentId).distinct().toList();
        Map<Long, Document> docsById = docIds.isEmpty()
            ? Map.of()
            : documentRepository.findAllById(docIds)
                .stream()
                .filter(d -> !d.isDeleted())
                .collect(Collectors.toMap(Document::getId, d -> d));

        List<Long> toUserIds = pageMovements.stream()
            .map(DocumentMovement::getToUserId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, String> userNamesById = toUserIds.isEmpty()
            ? Map.of()
            : userRepository.findAllById(toUserIds)
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        List<Long> toGroupIds = pageMovements.stream()
            .map(DocumentMovement::getToGroupId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        // Not Map.of(): almost every movement has a null toGroupId, and Map.of().get(null) throws
        // (unlike HashMap/Collections.emptyMap(), which return null for a missing/null key).
        Map<Long, String> groupNamesById = toGroupIds.isEmpty()
            ? java.util.Collections.emptyMap()
            : recipientGroupRepository.findAllById(toGroupIds)
                .stream()
                .collect(Collectors.toMap(RecipientGroup::getId, RecipientGroup::getName));

        List<Long> pageDocIds = pageMovements.stream().map(DocumentMovement::getDocumentId).distinct().toList();

        Map<Long, String> mainAttachmentTypes = pageDocIds.isEmpty()
            ? Map.of()
            : attachmentRepository
                .findByDocumentIdInAndDeletedFalseAndIsLatestTrue(pageDocIds)
                .stream()
                .collect(Collectors.toMap(
                    DocumentAttachment::getDocumentId,
                    a -> resolveAttachmentTypeFromFileName(a.getFileName()),
                    (first, second) -> first
                ));

        Map<Long, List<DocumentMovement>> inboundByDoc = pageDocIds.isEmpty()
            ? Map.of()
            : movementRepository
                .findByDocumentIdInAndToUserIdAndActionTypeInOrderByDocumentIdAscActionAtAsc(
                    pageDocIds,
                    actorUserId,
                    List.of(MovementActionType.CREATE, MovementActionType.FORWARD, MovementActionType.RETURN)
                )
                .stream()
                .collect(Collectors.groupingBy(DocumentMovement::getDocumentId));

        Map<Long, List<DocumentRemark>> ownRemarksByDoc = pageDocIds.isEmpty()
            ? Map.of()
            : remarkRepository
                .findByDocumentIdInAndRemarkedByUserIdOrderByDocumentIdAscRemarkedAtAsc(pageDocIds, actorUserId)
                .stream()
                .collect(Collectors.groupingBy(DocumentRemark::getDocumentId));
        Map<Long, List<AuditLog>> autoForwardLogsByDoc = pageDocIds.isEmpty()
            ? Map.of()
            : auditLogRepository
                .findByEntityTypeAndEntityIdInAndActionTypeOrderByPerformedAtAsc("MOVEMENT", pageDocIds, "AUTO_FORWARD_DC_TIMEOUT")
                .stream()
                .collect(Collectors.groupingBy(AuditLog::getEntityId));
        // Undo rows are derived from movement history so the sent list can explain why a send is no longer reversible.
        List<DocumentMovement> undoMovements = pageDocIds.isEmpty()
            ? List.of()
            : movementRepository.findByDocumentIdInAndActionTypeOrderByDocumentIdAscActionAtAsc(
                pageDocIds,
                MovementActionType.UNDO_SEND
            );
        Map<Long, List<DocumentMovement>> undoMovementsByDoc = undoMovements.stream()
            .collect(Collectors.groupingBy(DocumentMovement::getDocumentId));
        List<Long> undoActorIds = undoMovements.stream()
            .map(DocumentMovement::getActionByUserId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, User> undoActorsById = undoActorIds.isEmpty()
            ? Map.of()
            : userRepository.findAllById(undoActorIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, Long> latestMovementIdByDoc = pageDocIds.isEmpty()
            ? Map.of()
            : movementRepository.findLatestByDocumentIds(pageDocIds)
                .stream()
                .collect(Collectors.toMap(DocumentMovement::getDocumentId, DocumentMovement::getId));

        List<SentMessageResponse> sentRows = pageMovements.stream().map(movement -> {
            Document doc = docsById.get(movement.getDocumentId());
            // This preview is the sender's own minute (sourced from their own remarks), so it is always
            // shown to them — even if they are no longer a recipient and could not otherwise view minutes.
            String ownMinutePreview = toOwnSentMinutePreview(
                movement,
                inboundByDoc.getOrDefault(movement.getDocumentId(), List.of()),
                ownRemarksByDoc.getOrDefault(movement.getDocumentId(), List.of())
            );
            boolean autoForwarded = autoForwardLogsByDoc
                .getOrDefault(movement.getDocumentId(), List.of())
                .stream()
                .anyMatch(log -> log.getPerformedAt() != null
                    && movement.getActionAt() != null
                    && !log.getPerformedAt().isBefore(movement.getActionAt()));
            boolean undoNotice = movement.getActionType() == MovementActionType.UNDO_SEND;
            boolean isLatest = movement.getId() != null
                && movement.getId().equals(latestMovementIdByDoc.get(movement.getDocumentId()));
            UndoSendState undoState = undoNotice
                ? new UndoSendState(false, "UNDONE", null, false, "UNDO_SEND", false, true)
                : resolveUndoSendState(doc, movement, actorUserId, isLatest);
            DocumentMovement undoMovement = undoNotice
                ? movement
                : findUndoMovementForSentMovement(
                    movement,
                    undoMovementsByDoc.getOrDefault(movement.getDocumentId(), List.of())
                );
            User undoActor = undoMovement == null ? null : undoActorsById.get(undoMovement.getActionByUserId());
            RecipientSummaryResponse recipientSummary = doc == null
                ? null
                : documentRecipientService.summaryForSentMovement(doc, movement.getId(), actorUserId);
            return SentMessageResponse.builder()
                .movementId(movement.getId())
                .documentId(movement.getDocumentId())
                .refNo(doc == null ? null : doc.getRefNo())
                .title(doc == null ? null : doc.getTitle())
                .companyName(doc == null ? null : doc.getCompanyName())
                .priority(doc == null ? null : doc.getPriority())
                .status(doc == null ? null : doc.getStatus())
                .mainAttachmentType(mainAttachmentTypes.get(movement.getDocumentId()))
                .forwardVisibility(movement.getForwardVisibility())
                .toUserId(movement.getToUserId())
                .toUserName(userNamesById.get(movement.getToUserId()))
                .toGroupId(movement.getToGroupId())
                .toGroupName(groupNamesById.get(movement.getToGroupId()))
                .recipientSummary(recipientSummary)
                .recipientSummaryText(recipientSummary == null ? null : recipientSummary.getCompactText())
                .latestRemarkPreview(ownMinutePreview)
                .sentAt(movement.getActionAt())
                .autoForwarded(autoForwarded)
                .canUndoSend(undoState.canUndo())
                .undoSendStatus(undoState.status())
                .undoSendExpiresAt(undoState.expiresAt())
                .undoSendReceiverOpened(undoState.receiverOpened())
                .undoSendActionType(undoState.actionType())
                .undoSendRequiresReason(undoState.requiresReason())
                .undoSendShowExpiredInfo(undoState.showExpiredInfo())
                .undoSendByUserId(undoMovement == null ? null : undoMovement.getActionByUserId())
                .undoSendByName(undoActor == null ? null : undoActor.getFullName())
                .undoSendByRole(undoActor == null || undoActor.getRole() == null ? null : undoActor.getRole().getRoleName())
                .build();
            }).toList();

        return new PageImpl<>(sentRows, pageable, movementPage.getTotalElements());
    }

    private DocumentMovement findUndoMovementForSentMovement(DocumentMovement sentMovement, List<DocumentMovement> undoMovements) {
        if (sentMovement == null || undoMovements == null || undoMovements.isEmpty()) {
            return null;
        }
        return undoMovements.stream()
            .filter(undo -> java.util.Objects.equals(undo.getActionByUserId(), sentMovement.getActionByUserId()))
            .filter(undo -> java.util.Objects.equals(undo.getFromUserId(), sentMovement.getToUserId()))
            .filter(undo -> java.util.Objects.equals(undo.getToUserId(), sentMovement.getFromUserId()))
            .filter(undo -> undo.getActionAt() != null
                && sentMovement.getActionAt() != null
                && !undo.getActionAt().isBefore(sentMovement.getActionAt()))
            .findFirst()
            .orElse(null);
    }

    @Transactional
    @Override
    public DocumentResponse getDocumentById(Long id, Long actorUserId) {
        Document d = requireDocument(id);
        User actor = requireUser(actorUserId);
        ensureCanViewDocument(d, actorUserId);
        // Opening the document marks it as viewed for workload counts and undo-send receiver-open checks.
        markViewedByUser(d.getId(), actorUserId);
        markDcViewedIfNeeded(d, actor);

        Set<Long> docUserIds = new HashSet<>();
        if (d.getCreatedByUserId() != null) docUserIds.add(d.getCreatedByUserId());
        if (d.getCurrentOwnerUserId() != null) docUserIds.add(d.getCurrentOwnerUserId());
        Map<Long, User> docUsers = docUserIds.isEmpty() ? Map.of()
                : userRepository.findAllById(docUserIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        String createdByName = d.getCreatedByUserId() == null ? null
                : docUsers.getOrDefault(d.getCreatedByUserId(), null) == null ? null
                : docUsers.get(d.getCreatedByUserId()).getFullName();
        String ownerName = d.getCurrentOwnerUserId() == null ? null
                : docUsers.getOrDefault(d.getCurrentOwnerUserId(), null) == null ? null
                : docUsers.get(d.getCurrentOwnerUserId()).getFullName();
        String mainAttachmentType = resolveMainAttachmentType(d.getId());
        String latestRemarkPreview = canViewRemarks(d, actorUserId)
            ? remarkRepository.findFirstByDocumentIdOrderByRemarkedAtDesc(d.getId())
                .map(this::toRemarkPreview)
                .orElse(null)
            : null;
        UndoSendState undoState = movementRepository.findFirstByDocumentIdOrderByActionAtDescIdDesc(d.getId())
                .map(movement -> resolveUndoSendState(d, movement, actorUserId, true))
                .orElse(resolveUndoSendState(d, null, actorUserId, true));

        return DocumentResponse.from(withRecipientCapabilities(DocumentResponse.mapping(d)
                .createdByName(createdByName)
                .ownerName(ownerName)
                .mainAttachmentType(mainAttachmentType)
                .latestRemarkPreview(latestRemarkPreview)
                .viewedByMe(true)
                .canUndoSend(undoState.canUndo())
                .undoSendStatus(undoState.status())
                .undoSendExpiresAt(undoState.expiresAt())
                .undoSendReceiverOpened(undoState.receiverOpened())
                .undoSendActionType(undoState.actionType())
                .undoSendRequiresReason(undoState.requiresReason())
                .undoSendShowExpiredInfo(undoState.showExpiredInfo())
                .canDelete(canDeleteDocument(d, actorUserId)),
                d,
                actorUserId)
                .build());
    }

    @Transactional(readOnly = true)
    @Override
    public MyWorkloadStatsResponse getMyWorkloadStats(Long actorUserId) {
        long assignedCount = documentRepository.countAssignedActiveByOwner(actorUserId, Status.ISSUED);
        long openedCount = documentRepository.countOpenedAssignedActiveByOwner(actorUserId, Status.ISSUED);
        long unopenedCount = Math.max(0, assignedCount - openedCount);

        return MyWorkloadStatsResponse.builder()
                .assignedCount(assignedCount)
                .openedCount(openedCount)
                .unopenedCount(unopenedCount)
                .build();
    }

    @Override
    @Transactional
    public DocumentResponse updateDocument(Long id, UpdateDocumentRequest request, Long actorUserId) {
        Document d = requireDocument(id);

        permissionService.ensurePermission(actorUserId, AppPermission.EDIT_DOCUMENT_DETAILS, "You are not allowed to edit document details.");

        // Only the current owner (or, for a group-held document, a group admin) can edit details
        if (!canActOnDocument(d, actorUserId)) {
            throw new BadRequestException("Only the current owner can edit document details.");
        }

        // Lock editing after completion/issue
        if (d.getCompletedAt() != null || d.getStatus() == Status.ISSUED) {
            throw new BadRequestException("Cannot edit details after document is COMPLETED or ISSUED.");
        }

        String newRefNo = request.getRefNo() == null ? "" : request.getRefNo().trim();
        if (newRefNo.isEmpty()) {
            throw new BadRequestException("Ref No is required.");
        }

        if (documentRepository.existsByRefNoAndDeletedFalseAndIdNot(newRefNo, d.getId())) {
            throw new BadRequestException("Ref No already exists: " + newRefNo);
        }

        d.setRefNo(newRefNo);
        d.setTitle(request.getTitle().trim());
        d.setCompanyName(request.getCompanyName().trim());
        d.setReceivedDate(request.getReceivedDate());
        d.setPriority(request.getPriority());
        touchDocument(d);

        Document saved = documentRepository.save(d);

        auditLogService.logDocumentUpdate(saved.getId(), actorUserId, "Document updated");

        String createdByName = userRepository.findById(saved.getCreatedByUserId()).map(User::getFullName).orElse(null);
        String ownerName = userRepository.findById(saved.getCurrentOwnerUserId()).map(User::getFullName).orElse(null);
        String mainAttachmentType = resolveMainAttachmentType(saved.getId());

        String latestRemarkPreview = remarkRepository.findFirstByDocumentIdOrderByRemarkedAtDesc(saved.getId())
            .map(this::toRemarkPreview)
            .orElse(null);

        return DocumentResponse.from(withRecipientCapabilities(DocumentResponse.mapping(saved)
                .createdByName(createdByName)
                .ownerName(ownerName)
                .mainAttachmentType(mainAttachmentType)
                .latestRemarkPreview(latestRemarkPreview)
                .viewedByMe(true)
                .canDelete(canDeleteDocument(saved, actorUserId)),
                saved,
                actorUserId)
                .build());
    }

    @Override
    @Transactional
    public DocumentResponse updateRecipients(Long id, UpdateDocumentRecipientsRequest request, Long actorUserId) {
        Document d = requireDocument(id);
        ensureCanViewDocument(d, actorUserId);
        Map<String, List<Long>> previousRecipients = documentRecipientService.getActiveRecipientsByType(id);
        List<Long> newCcUserIds = request == null || request.getCcUserIds() == null ? List.of() : request.getCcUserIds();
        List<Long> newBccUserIds = request == null || request.getBccUserIds() == null ? List.of() : request.getBccUserIds();
        documentRecipientService.updateCopiedRecipients(
                d,
                newCcUserIds,
                newBccUserIds,
                actorUserId
        );
        touchDocument(d);
        documentRepository.save(d);

        Map<String, List<Long>> updatedRecipients = documentRecipientService.getActiveRecipientsByType(id);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("documentId", id);
        details.put("actorUserId", actorUserId);
        details.put("activeRecipientSetId", documentRecipientService.activeRecipientSetId(id).orElse(null));
        details.put("previousCcUserIds", previousRecipients.getOrDefault("cc", List.of()));
        details.put("previousBccUserIds", previousRecipients.getOrDefault("bcc", List.of()));
        details.put("ccUserIds", updatedRecipients.getOrDefault("cc", List.of()));
        details.put("bccUserIds", updatedRecipients.getOrDefault("bcc", List.of()));
        details.put("addedCcUserIds", addedIds(previousRecipients.getOrDefault("cc", List.of()), updatedRecipients.getOrDefault("cc", List.of())));
        details.put("removedCcUserIds", removedIds(previousRecipients.getOrDefault("cc", List.of()), updatedRecipients.getOrDefault("cc", List.of())));
        details.put("addedBccUserIds", addedIds(previousRecipients.getOrDefault("bcc", List.of()), updatedRecipients.getOrDefault("bcc", List.of())));
        details.put("removedBccUserIds", removedIds(previousRecipients.getOrDefault("bcc", List.of()), updatedRecipients.getOrDefault("bcc", List.of())));

        auditLogService.logEventWithDetails(
                "DOCUMENT",
                id,
                "RECIPIENTS_UPDATE",
                actorUserId,
                "Document recipients updated",
                details
        );

        return getDocumentById(id, actorUserId);
    }

    private List<Long> addedIds(List<Long> previousIds, List<Long> currentIds) {
        Set<Long> previous = new HashSet<>(previousIds == null ? List.of() : previousIds);
        return (currentIds == null ? List.<Long>of() : currentIds)
                .stream()
                .filter(id -> !previous.contains(id))
                .toList();
    }

    private List<Long> removedIds(List<Long> previousIds, List<Long> currentIds) {
        Set<Long> current = new HashSet<>(currentIds == null ? List.of() : currentIds);
        return (previousIds == null ? List.<Long>of() : previousIds)
                .stream()
                .filter(id -> !current.contains(id))
                .toList();
    }

    private boolean canDeleteDocument(Document document, Long actorUserId) {
        boolean canDeleteAny = permissionService.hasPermission(actorUserId, AppPermission.DELETE_ANY_DOCUMENT);
        boolean canDeleteOwn = permissionService.hasPermission(actorUserId, AppPermission.DELETE_DOCUMENT);
        return canDeleteDocument(document, actorUserId, canDeleteAny, canDeleteOwn);
    }

    // The Report At (current owner) may delete only with DELETE_DOCUMENT; DELETE_ANY_DOCUMENT allows
    // deleting any document regardless of ownership.
    private boolean canDeleteDocument(Document document, Long actorUserId,
                                      boolean canDeleteAnyDocument, boolean canDeleteOwnDocument) {
        if (document == null || actorUserId == null) return false;
        if (canDeleteAnyDocument) return true;
        return actorUserId.equals(document.getCurrentOwnerUserId()) && canDeleteOwnDocument;
    }

    private DocumentResponse.Mapping.MappingBuilder withRecipientCapabilities(
            DocumentResponse.Mapping.MappingBuilder builder,
            Document document,
            Long actorUserId
    ) {
        RecipientCapabilities capabilities = recipientCapabilities(document, actorUserId);
        return builder
                .recipientType(capabilities.recipientType())
                .recipientSummary(capabilities.recipientSummary())
                .canManageRecipients(capabilities.canManageRecipients())
                .canWorkflow(capabilities.canWorkflow())
                .canViewAttachments(capabilities.canViewAttachments())
                .canUploadAttachment(capabilities.canUploadAttachment())
                .canDeleteOwnAttachment(capabilities.canDeleteOwnAttachment())
                .canViewTimeline(capabilities.canViewTimeline())
                .canViewMinutes(capabilities.canViewMinutes());
    }

    private DocumentResponse.Mapping.MappingBuilder withPreloadedRecipientCapabilities(
            DocumentResponse.Mapping.MappingBuilder builder,
            boolean isOwner,
            RecipientType preloadedType,
            RecipientSummaryResponse preloadedSummary,
            Set<AppPermission> actorPermissions
    ) {
        boolean canViewHidden = actorPermissions.contains(AppPermission.VIEW_ALL_HISTORY);

        boolean canViewAttachments = isOwner || canViewHidden || (preloadedType == RecipientType.CC
                ? actorPermissions.contains(AppPermission.CC_VIEW_ATTACHMENTS)
                : preloadedType == RecipientType.BCC && actorPermissions.contains(AppPermission.BCC_VIEW_ATTACHMENTS));

        boolean canUpload = isOwner
                ? actorPermissions.contains(AppPermission.UPLOAD_ATTACHMENT)
                : (preloadedType == RecipientType.CC
                    ? actorPermissions.contains(AppPermission.CC_UPLOAD_ATTACHMENTS)
                    : preloadedType == RecipientType.BCC && actorPermissions.contains(AppPermission.BCC_UPLOAD_ATTACHMENTS))
                  && actorPermissions.contains(AppPermission.UPLOAD_ATTACHMENT);

        boolean canDeleteOwn = isOwner
                ? actorPermissions.contains(AppPermission.DELETE_ATTACHMENT)
                : (preloadedType == RecipientType.CC
                    ? actorPermissions.contains(AppPermission.CC_DELETE_OWN_ATTACHMENTS)
                    : preloadedType == RecipientType.BCC && actorPermissions.contains(AppPermission.BCC_DELETE_OWN_ATTACHMENTS))
                  && actorPermissions.contains(AppPermission.DELETE_ATTACHMENT);

        boolean canViewTimeline = isOwner || actorPermissions.contains(AppPermission.VIEW_ALL_HISTORY)
                || (preloadedType == RecipientType.CC
                    ? actorPermissions.contains(AppPermission.CC_VIEW_TIMELINE)
                    : preloadedType == RecipientType.BCC && actorPermissions.contains(AppPermission.BCC_VIEW_TIMELINE));

        boolean canViewMinutes = isOwner || actorPermissions.contains(AppPermission.VIEW_REMARKS_WHEN_NOT_REPORT_AT)
                || (preloadedType == RecipientType.CC
                    ? actorPermissions.contains(AppPermission.CC_VIEW_MINUTES)
                    : preloadedType == RecipientType.BCC && actorPermissions.contains(AppPermission.BCC_VIEW_MINUTES));

        boolean canManage = actorPermissions.contains(AppPermission.MANAGE_ANY_DOCUMENT_RECIPIENTS)
                || (isOwner && actorPermissions.contains(AppPermission.MANAGE_DOCUMENT_RECIPIENTS));

        RecipientSummaryResponse summary = preloadedSummary != null ? preloadedSummary
                : RecipientSummaryResponse.builder().to(List.of()).cc(List.of()).bcc(List.of()).compactText("").build();

        return builder
                .recipientType(preloadedType == null ? null : preloadedType.name())
                .recipientSummary(summary)
                .canManageRecipients(canManage)
                .canWorkflow(isOwner)
                .canViewAttachments(canViewAttachments)
                .canUploadAttachment(canUpload)
                .canDeleteOwnAttachment(canDeleteOwn)
                .canViewTimeline(canViewTimeline)
                .canViewMinutes(canViewMinutes);
    }

    private RecipientCapabilities recipientCapabilities(Document document, Long actorUserId) {
        boolean isWorkflowOwner = canActOnDocument(document, actorUserId);
        RecipientType recipientType = documentRecipientService.activeRecipientType(document.getId(), actorUserId)
                .orElse(isWorkflowOwner ? RecipientType.TO : null);
        return new RecipientCapabilities(
                recipientType == null ? null : recipientType.name(),
                documentRecipientService.summaryForViewer(document, actorUserId),
                documentRecipientService.canManageRecipients(document, actorUserId),
                isWorkflowOwner,
                documentRecipientService.canViewAttachments(document, actorUserId),
                documentRecipientService.canUploadAttachment(document, actorUserId),
                documentRecipientService.canDeleteOwnAttachment(document, actorUserId),
                documentRecipientService.canViewTimeline(document, actorUserId),
                documentRecipientService.canViewMinutes(document, actorUserId)
        );
    }

    private String resolveMainAttachmentType(Long documentId) {
        return attachmentRepository
                .findFirstByDocumentIdAndDeletedFalseAndIsLatestTrue(documentId)
                .map(DocumentAttachment::getFileName)
                .map(this::resolveAttachmentTypeFromFileName)
                .orElse(null);
    }

    private String resolveAttachmentTypeFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "FILE";

        String ext = fileName.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "pdf" -> "PDF";
            case "doc", "docx" -> "DOC";
            case "xls", "xlsx", "csv" -> "XLS";
            case "png", "jpg", "jpeg", "gif", "bmp", "webp" -> "IMG";
            case "txt" -> "TXT";
            case "zip", "rar", "7z" -> "ZIP";
            default -> "FILE";
        };
    }

    @Override
    @Transactional
    public void deleteDocument(Long id, Long actorUserId) {
        Document d = requireDocument(id);
        boolean isCurrentReportAtUser = actorUserId.equals(d.getCurrentOwnerUserId());
        boolean canDeleteAnyDocument = permissionService.hasPermission(actorUserId, AppPermission.DELETE_ANY_DOCUMENT);
        if (!canDeleteAnyDocument) {
            if (!isCurrentReportAtUser) {
                throw new BadRequestException("Only the current Report At user can delete this document.");
            }
            // The Report At owner additionally needs DELETE_DOCUMENT to remove their assigned document.
            permissionService.ensurePermission(actorUserId, AppPermission.DELETE_DOCUMENT, "You are not allowed to delete documents.");
        }

        String deletedByName = userRepository.findById(actorUserId).map(User::getFullName).orElse("Unknown user");
        String currentOwnerName = d.getCurrentOwnerUserId() == null
                ? null
                : userRepository.findById(d.getCurrentOwnerUserId()).map(User::getFullName).orElse(null);
        String createdByName = d.getCreatedByUserId() == null
                ? null
                : userRepository.findById(d.getCreatedByUserId()).map(User::getFullName).orElse(null);
        LocalDateTime deletedAt = LocalDateTime.now();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("documentId", d.getId());
        details.put("refNo", d.getRefNo());
        details.put("title", d.getTitle());
        details.put("companyName", d.getCompanyName());
        details.put("status", d.getStatus() == null ? null : d.getStatus().name());
        details.put("priority", d.getPriority() == null ? null : d.getPriority().name());
        details.put("visibility", d.getVisibility());
        details.put("currentOwnerUserId", d.getCurrentOwnerUserId());
        details.put("currentOwnerName", currentOwnerName);
        details.put("createdByUserId", d.getCreatedByUserId());
        details.put("createdByName", createdByName);
        details.put("deletedByUserId", actorUserId);
        details.put("deletedByName", deletedByName);
        details.put("deletedAt", deletedAt.toString());
        details.put("deleteScope", isCurrentReportAtUser ? "REPORT_AT_USER" : "DELETE_ANY_DOCUMENT");

        d.setDeleted(true);
        d.setDeletedAt(deletedAt);
        d.setDeletedByUserId(actorUserId);
        documentRepository.save(d);

        auditLogService.logEventWithDetails(
                "DOCUMENT",
                id,
                "DELETE",
                actorUserId,
                "Document deleted: " + d.getRefNo() + " - " + d.getTitle() + " by " + deletedByName,
                details
        );
    }

    // ==========================================================
    // WORKFLOW ACTIONS
    // ==========================================================

    @Override
    @Transactional
    public void forward(Long documentId, ForwardReturnRequest request, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.FORWARD_DOCUMENT, "You are not allowed to forward documents.");

        Document d = requireDocument(documentId);

        // FINAL STATE LOCK: cannot forward if already final / approved
        ensureCanForwardOrReturn(d);

        User actionBy = requireUser(actorUserId);

        boolean toGroup = request.getToGroupId() != null;
        boolean toPerson = request.getToUserId() != null;
        if (toGroup == toPerson) {
            throw new BadRequestException("Specify exactly one of toUserId or toGroupId to forward this document.");
        }

        // Ownership check (or, for a group-held document, a group admin)
        if (!canActOnDocument(d, actionBy.getId())) {
            throw new BadRequestException("Only the current owner can forward this document.");
        }

        String forwardVisibility = normalizeForwardVisibility(request.getForwardVisibility());
        if ("PRIVATE".equals(forwardVisibility)) {
            permissionService.ensurePermission(actorUserId, AppPermission.FORWARD_PRIVATE, "You are not allowed to forward as PRIVATE.");
        } else {
            permissionService.ensurePermission(actorUserId, AppPermission.FORWARD_PUBLIC, "You are not allowed to forward as PUBLIC.");
        }
        if (!forwardVisibility.equalsIgnoreCase(effectiveVisibility(d))) {
            permissionService.ensurePermission(actorUserId, AppPermission.CHANGE_DOCUMENT_VISIBILITY, "You are not allowed to change document visibility.");
        }

        // IMPORTANT: remark must be saved BEFORE ownership changes
        saveRemarkIfPresent(d, actionBy.getId(), request.getRemarkText(), "Remark added during forward");

        Long from = d.getCurrentOwnerUserId();

        // Resolve the forward target: a single person (unchanged path), or a group - which holds
        // the document via its lowest-id admin as a compatibility anchor, with its other members
        // (including any other admins) added as CC so they can view the document too. Either way,
        // "who can now act" is governed by canActOnDocument, not by who this anchor is.
        User toUser;
        Long toGroupId = null;
        List<Long> groupCcUserIds = List.of();
        Set<Long> otherGroupAdminIds = Set.of();
        if (toGroup) {
            RecipientGroup group = recipientGroupRepository.findById(request.getToGroupId())
                    .orElseThrow(() -> new BadRequestException("Group not found: " + request.getToGroupId()));
            List<RecipientGroupMember> members = recipientGroupMemberRepository.findByGroupId(group.getId());
            Long primaryAdminId = members.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsAdmin()))
                    .map(RecipientGroupMember::getUserId)
                    .min(Long::compareTo)
                    .orElseThrow(() -> new BadRequestException("This group has no admins to forward to."));
            toUser = requireUser(primaryAdminId);
            toGroupId = group.getId();
            groupCcUserIds = members.stream()
                    .map(RecipientGroupMember::getUserId)
                    .filter(id -> !id.equals(primaryAdminId))
                    .distinct()
                    .toList();
            // Other admins are full co-owners of the document (see canActOnDocument), so they get
            // notified the same way as the anchor rather than as a passive "copied" recipient.
            otherGroupAdminIds = members.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsAdmin()))
                    .map(RecipientGroupMember::getUserId)
                    .filter(id -> !id.equals(primaryAdminId))
                    .collect(Collectors.toSet());
            d.setCurrentOwnerGroupId(group.getId());
        } else {
            toUser = requireUser(request.getToUserId());
            // Forwarding to a person always exits group-held state.
            d.setCurrentOwnerGroupId(null);
        }
        Long to = toUser.getId();

        d.setCurrentOwnerUserId(to);
        // New owner should see the document as unopened until they explicitly open it.
        documentUserViewRepository.deleteByDocumentIdAndUserId(d.getId(), to);
        d.setVisibility(forwardVisibility);
        applyDcAutoForwardTrackingAfterOwnershipChange(d, toUser);
        d.setStatus(Status.IN_PROGRESS);
        touchDocument(d);
        documentRepository.save(d);

        List<Long> ccUserIds = request.getCcUserIds();
        if (!groupCcUserIds.isEmpty()) {
            java.util.LinkedHashSet<Long> merged = new java.util.LinkedHashSet<>(groupCcUserIds);
            if (request.getCcUserIds() != null) {
                merged.addAll(request.getCcUserIds());
            }
            merged.remove(to);
            ccUserIds = List.copyOf(merged);
        }

        DocumentMovement mv = DocumentMovement.create(documentId, from, to, actionBy.getId(), MovementActionType.FORWARD, forwardVisibility);
        mv.setToGroupId(toGroupId);
        DocumentMovement savedMovement = movementRepository.save(mv);
        documentRecipientService.createForwardSet(d, to, ccUserIds, request.getBccUserIds(), actionBy.getId(), savedMovement.getId());
        Map<String, List<Long>> forwardRecipients = documentRecipientService.getActiveRecipientsByType(documentId);
        Map<String, Object> forwardDetails = new LinkedHashMap<>();
        forwardDetails.put("fromUserId", from);
        forwardDetails.put("toUserId", to);
        forwardDetails.put("toGroupId", toGroupId);
        forwardDetails.put("toUserIds", forwardRecipients.getOrDefault("to", List.of()));
        forwardDetails.put("ccUserIds", forwardRecipients.getOrDefault("cc", List.of()));
        forwardDetails.put("bccUserIds", forwardRecipients.getOrDefault("bcc", List.of()));
        forwardDetails.put("forwardVisibility", forwardVisibility);
        forwardDetails.put("movementId", savedMovement.getId());
        forwardDetails.put("activeRecipientSetId", documentRecipientService.activeRecipientSetId(documentId).orElse(null));

        auditLogService.logEventWithDetails(
                "MOVEMENT",
                documentId,
                "FORWARD",
                actionBy.getId(),
                "Forwarded to userId=" + to + " with visibility=" + forwardVisibility,
                forwardDetails
        );
        realtimeNotificationService.notifyDocumentForwarded(
            to,
            d.getId(),
            d.getRefNo(),
            d.getTitle(),
            actionBy.getId(),
            actionBy.getFullName()
        );
        // Other group admins are full co-owners (see canActOnDocument) - notify them the same way
        // as the anchor, not as a passive "copied" recipient. (Captured into a final copy so it
        // can be referenced from the lambdas below; otherGroupAdminIds itself is reassigned above.)
        final Set<Long> notifiedAsAdminIds = otherGroupAdminIds;
        notifiedAsAdminIds.forEach(adminId -> realtimeNotificationService.notifyDocumentForwarded(
                adminId,
                d.getId(),
                d.getRefNo(),
                d.getTitle(),
                actionBy.getId(),
                actionBy.getFullName()));
        // Remaining CC/BCC recipients are told in real time that they were copied (distinct from
        // the "assigned" notification the report-at recipient and other group admins receive).
        java.util.stream.Stream.concat(
                        forwardRecipients.getOrDefault("cc", List.of()).stream(),
                        forwardRecipients.getOrDefault("bcc", List.of()).stream())
                .filter(java.util.Objects::nonNull)
                .filter(copiedUserId -> !copiedUserId.equals(to))
                .filter(copiedUserId -> !notifiedAsAdminIds.contains(copiedUserId))
                .distinct()
                .forEach(copiedUserId -> realtimeNotificationService.notifyDocumentCopied(
                        copiedUserId,
                        d.getId(),
                        d.getRefNo(),
                        d.getTitle(),
                        actionBy.getId(),
                        actionBy.getFullName()));
    }

    @Override
    @Transactional
    public void returns(Long documentId, ForwardReturnRequest request, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.RETURN_DOCUMENT, "You are not allowed to return documents.");

        Document d = requireDocument(documentId);

        // FINAL STATE LOCK
        ensureCanForwardOrReturn(d);

        User actionBy = requireUser(actorUserId);
        // Ownership check (or, for a group-held document, a group admin)
        if (!canActOnDocument(d, actionBy.getId())) {
            throw new BadRequestException("Only the current owner can return this document.");
        }

        // Save remark BEFORE ownership change
        saveRemarkIfPresent(d, actionBy.getId(), request.getRemarkText(), "Remark added during return");

        Long from = d.getCurrentOwnerUserId();
        // Return is always sent back to whoever forwarded the document here. Derive that target from the
        // movement history (the most recent forward/return addressed to the current owner) instead of
        // trusting the client-supplied toUserId, so a direct API call cannot reroute the document to an
        // arbitrary user.
        Long requestedTo = movementRepository
                .findFirstByDocumentIdAndToUserIdAndActionTypeInOrderByActionAtDescIdDesc(
                        documentId, from, List.of(MovementActionType.FORWARD, MovementActionType.RETURN))
                .map(DocumentMovement::getFromUserId)
                .orElse(null);
        if (requestedTo == null) {
            throw new BadRequestException("This document was not sent to you, so it cannot be returned.");
        }
        d.setCurrentOwnerUserId(requestedTo);
        // Returning always hands the document back to a single person, so it is no longer group-held.
        d.setCurrentOwnerGroupId(null);

        DocumentMovement mv = DocumentMovement.create(documentId, from, requestedTo, actionBy.getId(), MovementActionType.RETURN);
        DocumentMovement savedMovement = movementRepository.save(mv);
        documentRecipientService.restorePreviousSetForTo(d, requestedTo, actionBy.getId(), savedMovement.getId(), RecipientSetReason.RETURN);
        Long to = d.getCurrentOwnerUserId();
        User toUser = requireUser(to);

        // Return also creates a fresh unread inbox item for the receiver.
        documentUserViewRepository.deleteByDocumentIdAndUserId(d.getId(), to);
        applyDcAutoForwardTrackingAfterOwnershipChange(d, toUser);
        d.setStatus(Status.RETURNED);
        touchDocument(d);
        documentRepository.save(d);

        Map<String, List<Long>> recipients = documentRecipientService.getActiveRecipientsByType(documentId);
        Map<String, Object> returnDetails = new LinkedHashMap<>();
        returnDetails.put("fromUserId", from);
        returnDetails.put("toUserId", to);
        returnDetails.put("toUserIds", recipients.getOrDefault("to", List.of()));
        returnDetails.put("ccUserIds", recipients.getOrDefault("cc", List.of()));
        returnDetails.put("bccUserIds", recipients.getOrDefault("bcc", List.of()));
        returnDetails.put("movementId", savedMovement.getId());
        returnDetails.put("restoredRecipientSetId", documentRecipientService.activeRecipientSetId(documentId).orElse(null));

        auditLogService.logEventWithDetails(
                "MOVEMENT",
                documentId,
                "RETURN",
                actionBy.getId(),
                "Returned to userId=" + to,
                returnDetails
        );
        realtimeNotificationService.notifyDocumentReturned(
            to,
            d.getId(),
            d.getRefNo(),
            d.getTitle(),
            actionBy.getId(),
            actionBy.getFullName()
        );
    }

    @Override
    @Transactional
    public void undoSend(Long documentId, UndoSendRequest request, Long actorUserId) {
        Document d = requireDocument(documentId);
        User actor = requireUser(actorUserId);
        DocumentMovement latestMovement = movementRepository.findFirstByDocumentIdOrderByActionAtDescIdDesc(documentId)
                .orElseThrow(() -> new BadRequestException("No sent movement found to undo."));

        UndoSendState state = resolveUndoSendState(d, latestMovement, actorUserId, true);
        if (!state.canUndo()) {
            throw new BadRequestException(undoBlockedMessage(state.status()));
        }

        String reason = request == null || request.getReason() == null ? "" : request.getReason().trim();
        if (state.requiresReason() && reason.isEmpty()) {
            throw new BadRequestException("Undo Send requires a reason.");
        }

        // Undo Send reverses only the latest forward/return movement and restores ownership to its sender.
        Long receiverUserId = latestMovement.getToUserId();
        Long senderUserId = latestMovement.getFromUserId();
        if (senderUserId == null) {
            throw new BadRequestException("Cannot undo this movement because the sender is unknown.");
        }

        DocumentMovement undoMovement = DocumentMovement.create(
                documentId,
                receiverUserId,
                senderUserId,
                actorUserId,
                MovementActionType.UNDO_SEND,
                latestMovement.getForwardVisibility()
        );
        DocumentMovement savedUndoMovement = movementRepository.save(undoMovement);
        d.setCurrentOwnerUserId(senderUserId);
        // Undo always hands the document back to a single person (the original sender), so it is
        // no longer group-held - without this, canActOnDocument() would keep granting action rights
        // to admins of whatever group the undone forward had targeted.
        d.setCurrentOwnerGroupId(null);
        documentRecipientService.restorePreviousSetForTo(d, senderUserId, actorUserId, savedUndoMovement.getId(), RecipientSetReason.UNDO_SEND);
        d.setStatus(Status.IN_PROGRESS);
        d.setCompletedAt(null);
        documentUserViewRepository.deleteByDocumentIdAndUserId(d.getId(), senderUserId);
        applyDcAutoForwardTrackingAfterOwnershipChange(d, actor);
        touchDocument(d);
        documentRepository.save(d);

        Map<String, List<Long>> undoRecipients = documentRecipientService.getActiveRecipientsByType(documentId);
        Map<String, Object> undoDetails = new LinkedHashMap<>();
        undoDetails.put("fromUserId", receiverUserId);
        undoDetails.put("toUserId", d.getCurrentOwnerUserId());
        undoDetails.put("toUserIds", undoRecipients.getOrDefault("to", List.of()));
        undoDetails.put("ccUserIds", undoRecipients.getOrDefault("cc", List.of()));
        undoDetails.put("bccUserIds", undoRecipients.getOrDefault("bcc", List.of()));
        undoDetails.put("previousMovementId", latestMovement.getId());
        undoDetails.put("undoMovementId", savedUndoMovement.getId());
        undoDetails.put("restoredRecipientSetId", documentRecipientService.activeRecipientSetId(documentId).orElse(null));
        undoDetails.put("reason", reason);

        auditLogService.logEventWithDetails(
                "MOVEMENT",
                documentId,
                "UNDO_SEND",
                actorUserId,
                "Undo send from userId=" + receiverUserId + " back to userId=" + d.getCurrentOwnerUserId()
                        + (reason.isBlank() ? "" : ". Reason: " + reason),
                undoDetails
        );

        var config = dcAutoForwardConfigService.getOrCreateEntity();
        if (config.isUndoSendNotifyReceiver()) {
            realtimeNotificationService.notifyDocumentUndoSend(
                    receiverUserId,
                    d.getId(),
                    d.getRefNo(),
                    d.getTitle(),
                    actor.getId(),
                    actor.getFullName()
            );
        }
        realtimeNotificationService.notifyDocumentUndoReturnedToSender(
                d.getCurrentOwnerUserId(),
                d.getId(),
                d.getRefNo(),
                d.getTitle(),
                actor.getId(),
                actor.getFullName()
        );
    }

    @Override
    @Transactional
    public void approve(Long documentId, DecisionRequest request, Long actorUserId) {
        if (!dcAutoForwardConfigService.isApproveRejectButtonsEnabled()) {
            throw new BadRequestException("Approve action is disabled by admin workflow settings.");
        }
        permissionService.ensurePermission(actorUserId, AppPermission.APPROVE_DOCUMENT, "You are not allowed to approve documents.");

        Document d = requireDocument(documentId);

        // FINAL STATE + transition guard
        ensureCanApproveOrReject(d);

        // DC must be current owner (or a group admin, if the document is group-held)
        if (!canActOnDocument(d, actorUserId)) {
            throw new BadRequestException("Only the current owner can approve this document.");
        }

        saveRemarkIfPresent(d, actorUserId, request.getRemarkText(), "Remark added during approve");

        d.setStatus(Status.APPROVED);
        d.setCompletedAt(LocalDateTime.now());
        touchDocument(d);
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, d.getCurrentOwnerUserId(), null, actorUserId, MovementActionType.APPROVE);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actorUserId, "APPROVE", "Approved by DC");
    }

    @Override
    @Transactional
    public void reject(Long documentId, DecisionRequest request, Long actorUserId) {
        if (!dcAutoForwardConfigService.isApproveRejectButtonsEnabled()) {
            throw new BadRequestException("Reject action is disabled by admin workflow settings.");
        }
        permissionService.ensurePermission(actorUserId, AppPermission.REJECT_DOCUMENT, "You are not allowed to reject documents.");

        Document d = requireDocument(documentId);

        // FINAL STATE + transition guard
        ensureCanApproveOrReject(d);

        if (!canActOnDocument(d, actorUserId)) {
            throw new BadRequestException("Only the current owner can reject this document.");
        }

        saveRemarkIfPresent(d, actorUserId, request.getRemarkText(), "Remark added during reject");

        d.setStatus(Status.REJECTED);
        d.setCompletedAt(LocalDateTime.now());
        touchDocument(d);
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, d.getCurrentOwnerUserId(), null, actorUserId, MovementActionType.REJECT);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actorUserId, "REJECT", "Rejected by DC");
    }

    @Override
    @Transactional
    public void issue(Long documentId, DecisionRequest request, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.ISSUE_DOCUMENT, "You are not allowed to complete documents.");

        Document d = requireDocument(documentId);

        // ISSUE must happen ONLY after APPROVED
        ensureCanIssue(d);

        if (!canActOnDocument(d, actorUserId)) {
            throw new BadRequestException("Only the current owner can complete this document.");
        }

        saveRemarkIfPresent(d, actorUserId, request.getRemarkText(), "Remark added during issue");

        d.setStatus(Status.ISSUED);
        d.setIssuedAt(LocalDateTime.now());
        touchDocument(d);
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, d.getCurrentOwnerUserId(), null, actorUserId, MovementActionType.ISSUE);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actorUserId, "ISSUE", "Issued by DC");
    }

    // ==========================================================
    // NEW: REOPEN
    // ==========================================================

    @Override
    @Transactional
    public void reopen(Long documentId, DecisionRequest request, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.REOPEN_DOCUMENT, "You are not allowed to reopen documents.");

        Document d = requireDocument(documentId);

        if (!dcAutoForwardConfigService.isApproveRejectButtonsEnabled()) {
            if (d.getStatus() != Status.ISSUED && d.getStatus() != Status.APPROVED && d.getStatus() != Status.REJECTED) {
                throw new BadRequestException("Reopen is allowed only for DONE, APPROVED, or REJECTED documents.");
            }
        } else {
            // ISSUED is final - cannot reopen
            if (d.getStatus() == Status.ISSUED) {
                throw new BadRequestException("Cannot reopen an ISSUED document.");
            }

            // Only APPROVED or REJECTED can be reopened
            if (d.getStatus() != Status.APPROVED && d.getStatus() != Status.REJECTED) {
                throw new BadRequestException("Reopen is allowed only for APPROVED or REJECTED documents.");
            }
        }

        // DC must also be the current owner (or a group admin, if group-held) - strong integrity
        if (!canActOnDocument(d, actorUserId)) {
            throw new BadRequestException("Only the current owner can reopen this document.");
        }

        // Reason required
        String reason = request.getRemarkText() == null ? "" : request.getRemarkText().trim();
        if (reason.isEmpty()) {
            throw new BadRequestException("Reopen requires a reason (remarkText must not be empty).");
        }

        // Save reason as remark (security rule inside method = must be current owner)
        saveRemarkIfPresent(d, actorUserId, reason, "Remark added during reopen");

        // Move back to active workflow
        d.setStatus(Status.IN_PROGRESS);

        // Decision is no longer final, so clear completed date
        d.setCompletedAt(null);
        touchDocument(d);

        // Movement: REOPEN (owner unchanged; log from->to as same owner)
        Long owner = d.getCurrentOwnerUserId();
        DocumentMovement mv = DocumentMovement.create(documentId, owner, owner, actorUserId, MovementActionType.REOPEN);
        DocumentMovement savedMovement = movementRepository.save(mv);
        documentRecipientService.createOwnerOnlySet(d, actorUserId, actorUserId, savedMovement.getId(), RecipientSetReason.REOPEN);
        documentRepository.save(d);

        auditLogService.logEventWithDetails(
                "MOVEMENT",
                documentId,
                "REOPEN",
                actorUserId,
                "Reopened by DC with reason: " + reason,
                Map.of(
                        "reopenedByUserId", actorUserId,
                        "toUserId", actorUserId,
                        "reason", reason,
                        "movementId", savedMovement.getId()
                )
        );
    }

    // ==========================================================
    // STATUS GUARDS (CRITICAL)
    // ==========================================================

    private void ensureCanForwardOrReturn(Document d) {
        if (!dcAutoForwardConfigService.isForwardReturnAllowed(d.getStatus())) {
            String allowed = dcAutoForwardConfigService.getForwardReturnAllowedStatuses()
                    .stream()
                    .map(Status::name)
                    .collect(Collectors.joining(", "));
            throw new BadRequestException("Cannot forward/return a " + d.getStatus()
                    + " document. Allowed statuses: " + allowed + ".");
        }
    }

    private void ensureCanApproveOrReject(Document d) {
        if (d.getStatus() == Status.ISSUED) {
            throw new BadRequestException("Cannot approve/reject an ISSUED document.");
        }
        if (d.getStatus() == Status.REJECTED) {
            throw new BadRequestException("Cannot approve/reject a REJECTED document.");
        }
        if (d.getStatus() == Status.APPROVED) {
            throw new BadRequestException("Document is already APPROVED. Only ISSUE is allowed.");
        }
    }

    private void ensureCanIssue(Document d) {
        if (!dcAutoForwardConfigService.isApproveRejectButtonsEnabled()) {
            if (d.getStatus() == Status.ISSUED) {
                throw new BadRequestException("Document is already ISSUED.");
            }
            return;
        }
        if (d.getStatus() == Status.ISSUED) {
            throw new BadRequestException("Document is already ISSUED.");
        }
        if (d.getStatus() == Status.REJECTED) {
            throw new BadRequestException("Cannot issue a REJECTED document.");
        }
        if (d.getStatus() != Status.APPROVED) {
            throw new BadRequestException("Cannot issue. Document must be APPROVED first.");
        }
    }

    private UndoSendState resolveUndoSendState(Document doc, DocumentMovement movement, Long actorUserId) {
        return resolveUndoSendState(doc, movement, actorUserId, false);
    }

    private UndoSendState resolveUndoSendState(Document doc, DocumentMovement movement, Long actorUserId, boolean isLatestMovement) {
        var config = dcAutoForwardConfigService.getOrCreateEntity();
        boolean requiresReason = config.isUndoSendRequiresReason();
        boolean showExpiredInfo = config.isUndoSendShowExpiredInfo();

        if (movement == null) {
            return new UndoSendState(false, "NO_SENT_MOVEMENT", null, false, null, requiresReason, showExpiredInfo);
        }

        LocalDateTime sentAt = movement.getActionAt();
        Integer configuredHours = config.getUndoSendWindowHours();
        int windowHours = configuredHours == null || configuredHours < 1 ? 24 : configuredHours;
        LocalDateTime expiresAt = sentAt == null ? null : sentAt.plusHours(windowHours);
        boolean receiverOpened = isReceiverOpenedAfterMovement(movement);
        String actionType = movement.getActionType() == null ? null : movement.getActionType().name();

        // The checks below are ordered from admin/configuration gates to document-state gates for clearer UI reasons.
        if (!config.isUndoSendEnabled()) {
            return new UndoSendState(false, "DISABLED", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (doc == null || doc.isDeleted()) {
            return new UndoSendState(false, "DOCUMENT_UNAVAILABLE", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (movement.getActionType() != MovementActionType.FORWARD && movement.getActionType() != MovementActionType.RETURN) {
            // The latest action was not a forward/return (e.g. a freshly created document), so there is
            // no "send" to undo. This is not the same as the document having moved on past a recipient,
            // so report NO_SENT_MOVEMENT (which the UI shows nothing for) rather than ALREADY_MOVED.
            return new UndoSendState(false, "NO_SENT_MOVEMENT", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (!dcAutoForwardConfigService.getUndoSendAllowedActions().contains(movement.getActionType())) {
            return new UndoSendState(false, "ACTION_NOT_ALLOWED", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (movement.getFromUserId() == null || !movement.getFromUserId().equals(actorUserId)) {
            return new UndoSendState(false, "NOT_SENDER", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (doc.getStatus() == Status.ISSUED || doc.getStatus() == Status.REJECTED || doc.getCompletedAt() != null) {
            return new UndoSendState(false, "FINALIZED", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (doc.getCurrentOwnerUserId() == null || !doc.getCurrentOwnerUserId().equals(movement.getToUserId())) {
            return new UndoSendState(false, "ALREADY_MOVED", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        boolean confirmedLatest = isLatestMovement || movementRepository.findFirstByDocumentIdOrderByActionAtDescIdDesc(doc.getId())
                .map(latest -> latest.getId() != null && latest.getId().equals(movement.getId()))
                .orElse(false);
        if (!confirmedLatest) {
            return new UndoSendState(false, "ALREADY_MOVED", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return new UndoSendState(false, "EXPIRED", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (config.isUndoSendRequiresUnopened() && receiverOpened) {
            return new UndoSendState(false, "OPENED", expiresAt, true, actionType, requiresReason, showExpiredInfo);
        }

        return new UndoSendState(true, "AVAILABLE", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
    }

    private boolean isReceiverOpenedAfterMovement(DocumentMovement movement) {
        if (movement == null || movement.getDocumentId() == null || movement.getToUserId() == null || movement.getActionAt() == null) {
            return false;
        }
        return documentUserViewRepository.findByDocumentIdAndUserId(movement.getDocumentId(), movement.getToUserId())
                .map(DocumentUserView::getViewedAt)
                .map(viewedAt -> !viewedAt.isBefore(movement.getActionAt()))
                .orElse(false);
    }

    private String undoBlockedMessage(String status) {
        return switch (status == null ? "" : status) {
            case "DISABLED" -> "Undo Send is disabled by admin.";
            case "ACTION_NOT_ALLOWED" -> "Undo Send is not allowed for this action.";
            case "NOT_SENDER" -> "Only the sender can undo this sent document.";
            case "FINALIZED" -> "Cannot undo a finalized document.";
            case "ALREADY_MOVED" -> "Cannot undo because the document has already moved.";
            case "EXPIRED" -> "Undo Send has expired.";
            case "OPENED" -> "Cannot undo because the receiver has already opened the document.";
            default -> "Undo Send is not available for this document.";
        };
    }

    // ==========================================================
    // REMARK HELPER
    // ==========================================================

    private void saveRemarkIfPresent(Document doc, Long actionByUserId, String remarkText, String auditMessage) {
        if (remarkText == null) return;

        permissionService.ensurePermission(actionByUserId, AppPermission.ADD_REMARK, "You are not allowed to add minutes.");

        String text = remarkText.trim();
        if (text.isEmpty()) return;

        // Only current owner (or, for a group-held document, a group admin) can add remark
        if (!canActOnDocument(doc, actionByUserId)) {
            throw new BadRequestException("Only the current owner can add remarks.");
        }

        DocumentRemark remark = DocumentRemark.builder()
                .documentId(doc.getId())
                .remarkText(text)
                .remarkedByUserId(actionByUserId)
                .remarkedAt(LocalDateTime.now())
                .build();

        DocumentRemark saved = remarkRepository.save(remark);
        touchDocument(doc);
        documentRepository.save(doc);

        auditLogService.logRemark(doc.getId(), actionByUserId, "REMARK", auditMessage, saved.getId());
    }

    private void touchDocument(Document doc) {
        if (doc == null) return;
        doc.setUpdatedAt(LocalDateTime.now());
    }

    // ==========================================================
    // COMMON HELPERS
    // ==========================================================

    private Document requireDocument(Long documentId) {
        return documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    // Person-held: you are the current owner. Group-held (current_owner_group_id set): any admin
    // of that group can act, mirroring co-ownership in a WhatsApp-style group. Regular group
    // members are not owners here - they get CC-level access via the recipient set instead.
    private boolean canActOnDocument(Document doc, Long userId) {
        if (doc.getCurrentOwnerUserId().equals(userId)) {
            return true;
        }
        return doc.getCurrentOwnerGroupId() != null
                && recipientGroupMemberRepository.isGroupAdmin(doc.getCurrentOwnerGroupId(), userId);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found: " + userId));
    }

    private boolean isRole(User user, String roleName) {
        return user.getRole() != null && roleName.equalsIgnoreCase(user.getRole().getRoleName());
    }

    private String normalizeForwardVisibility(String visibility) {
        String value = visibility == null ? "" : visibility.trim().toUpperCase();
        if (!"PRIVATE".equals(value) && !"PUBLIC".equals(value)) {
            throw new BadRequestException("forwardVisibility must be PRIVATE or PUBLIC.");
        }
        return value;
    }

    private void ensureCanViewDocument(Document doc, Long actorUserId) {
        if (permissionService.hasPermission(actorUserId, AppPermission.VIEW_ALL_DOCUMENTS)) {
            return;
        }

        if (canActOnDocument(doc, actorUserId)) {
            return;
        }

        if (documentRecipientService.canViewAsCopiedRecipient(doc, actorUserId)) {
            return;
        }

        String visibility = effectiveVisibility(doc);
        if ("PUBLIC".equals(visibility)) {
            if (permissionService.hasPermission(actorUserId, AppPermission.VIEW_PUBLIC_DOCUMENT)) {
                return;
            }
            throw new BadRequestException("You are not allowed to view this public document.");
        }

        if (permissionService.hasPermission(actorUserId, AppPermission.VIEW_OWN_CREATED_DOCUMENTS)
                && actorUserId.equals(doc.getCreatedByUserId())) {
            return;
        }

        if (permissionService.hasPermission(actorUserId, AppPermission.VIEW_PRIVATE_DOCUMENT)) {
            if (actorUserId.equals(doc.getCurrentOwnerUserId())) {
                return;
            }
            if (movementRepository.existsPrivateForwardTrailForUser(doc.getId(), actorUserId, MovementActionType.FORWARD)) {
                return;
            }
        }

        throw new BadRequestException("You are not allowed to view this private document.");
    }

    private String effectiveVisibility(Document doc) {
        String value = doc.getVisibility();
        if (value == null || value.isBlank()) {
            // Fail closed: an unknown/missing visibility is treated as PRIVATE (the more restrictive
            // option) so access control never accidentally exposes a document. All created documents
            // set visibility explicitly, so this only guards legacy/hand-inserted rows.
            return "PRIVATE";
        }
        return value.trim().toUpperCase();
    }

    private void applyDcAutoForwardTrackingAfterOwnershipChange(Document doc, User toUser) {
        if (isRole(toUser, DC_ROLE_NAME)) {
            doc.setDcAssignedAt(LocalDateTime.now());
            doc.setDcViewedAt(null);
            return;
        }

        doc.setDcAssignedAt(null);
        doc.setDcViewedAt(null);
    }

    private void markDcViewedIfNeeded(Document doc, User actor) {
        if (!isRole(actor, DC_ROLE_NAME)) return;
        if (!actor.getId().equals(doc.getCurrentOwnerUserId())) return;
        if (doc.getDcAssignedAt() == null || doc.getDcViewedAt() != null) return;

        doc.setDcViewedAt(LocalDateTime.now());
        documentRepository.save(doc);
    }

    private void markViewedByUser(Long documentId, Long actorUserId) {
        LocalDateTime now = LocalDateTime.now();
        DocumentUserView view = documentUserViewRepository.findByDocumentIdAndUserId(documentId, actorUserId)
                .orElseGet(() -> {
                    DocumentUserView created = new DocumentUserView();
                    created.setDocumentId(documentId);
                    created.setUserId(actorUserId);
                    return created;
                });

        view.setViewedAt(now);
        documentUserViewRepository.save(view);
    }

    private String toOwnSentMinutePreview(DocumentMovement sentMovement,
                                          List<DocumentMovement> inboundMovements,
                                          List<DocumentRemark> ownRemarks) {
        if (sentMovement == null || ownRemarks == null || ownRemarks.isEmpty()) {
            return null;
        }

        LocalDateTime sentAt = sentMovement.getActionAt();
        if (sentAt == null) {
            return null;
        }

        LocalDateTime latestReceivedAt = inboundMovements == null
            ? null
            : inboundMovements.stream()
                .map(DocumentMovement::getActionAt)
                .filter(t -> t != null && !t.isAfter(sentAt))
                .max(LocalDateTime::compareTo)
                .orElse(null);

        DocumentRemark matched = ownRemarks.stream()
            .filter(r -> r.getRemarkedAt() != null)
            .filter(r -> !r.getRemarkedAt().isAfter(sentAt))
            .filter(r -> latestReceivedAt == null || !r.getRemarkedAt().isBefore(latestReceivedAt))
            .max((a, b) -> a.getRemarkedAt().compareTo(b.getRemarkedAt()))
            .orElse(null);

        return matched == null ? null : toRemarkPreview(matched);
    }

    private boolean canViewRemarks(Document document, Long actorUserId) {
        if (document == null || actorUserId == null) {
            return false;
        }
        if (canActOnDocument(document, actorUserId)) {
            return true;
        }
        return documentRecipientService.canViewMinutes(document, actorUserId);
    }

    private Long resolveInboxSenderUserId(DocumentMovement movement) {
        if (movement == null) {
            return null;
        }
        if (movement.getFromUserId() != null) {
            return movement.getFromUserId();
        }
        return movement.getActionByUserId();
    }

    private String roleName(User user) {
        return user == null || user.getRole() == null ? null : user.getRole().getRoleName();
    }

    private String normalizeSearch(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Status parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Status.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            throw new BadRequestException("Invalid status filter: " + value);
        }
    }

    private Priority parsePriority(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Priority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            throw new BadRequestException("Invalid priority filter: " + value);
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            throw new BadRequestException("Invalid date filter: " + value);
        }
    }

    private Sort resolveDocumentSort(String sort) {
        return switch (String.valueOf(sort == null ? "recent" : sort).trim().toLowerCase()) {
            case "ref_asc" -> Sort.by(Sort.Order.asc("refNo"), Sort.Order.desc("id"));
            case "ref_desc" -> Sort.by(Sort.Order.desc("refNo"), Sort.Order.desc("id"));
            case "title_asc" -> Sort.by(Sort.Order.asc("title"), Sort.Order.desc("id"));
            case "priority_desc" -> Sort.by(Sort.Order.desc("priority"), Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
            case "status_asc" -> Sort.by(Sort.Order.asc("status"), Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
            case "days_open_desc" -> Sort.by(Sort.Order.asc("receivedDate"), Sort.Order.desc("id"));
            case "days_open_asc" -> Sort.by(Sort.Order.desc("receivedDate"), Sort.Order.desc("id"));
            case "recent" -> Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            default -> Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
    }

    private String toRemarkPreview(String value) {
        String text = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) return null;
        if (text.length() <= 110) return text;
        return text.substring(0, 107) + "...";
    }

    private String toRemarkPreview(DocumentRemark remark) {
        if (remark == null || remark.getRemarkText() == null) {
            return null;
        }
        
        String text = remark.getRemarkText().trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) return null;
        
        // Truncate text to 80 chars to fit author/time metadata
        String preview = text.length() <= 80 ? text : text.substring(0, 77) + "...";
        
        // Format author and timestamp
        String authorName = remark.getRemarkedBy() != null ? remark.getRemarkedBy().getFullName() : null;
        if (authorName == null || authorName.isBlank()) {
            authorName = "Unknown";
        }
        String roleName = remark.getRemarkedBy() != null && remark.getRemarkedBy().getRole() != null
            ? remark.getRemarkedBy().getRole().getRoleName()
            : "";
        String authorLabel = (roleName == null || roleName.isBlank())
            ? authorName
            : String.format("%s (%s)", authorName, roleName);
        
        LocalDateTime remarkedAt = remark.getRemarkedAt();
        String formattedTime;
        if (remarkedAt == null) {
            formattedTime = "time unknown";
        } else {
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a");
            formattedTime = remarkedAt.format(timeFormatter);
        }
        
        return String.format("By %s • %s – %s", authorLabel, formattedTime, preview);
    }

}
