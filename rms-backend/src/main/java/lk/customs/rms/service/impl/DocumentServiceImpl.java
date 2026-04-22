package lk.customs.rms.service.impl;

import lk.customs.rms.dto.*;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentAttachment;
import lk.customs.rms.entity.DocumentMovement;
import lk.customs.rms.entity.DocumentRemark;
import lk.customs.rms.entity.DocumentUserView;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.enums.MovementActionType;
import lk.customs.rms.enums.Status;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.exception.ResourceNotFoundException;
import lk.customs.rms.repository.DocumentMovementRepository;
import lk.customs.rms.repository.DocumentAttachmentRepository;
import lk.customs.rms.repository.DocumentRemarkRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.DocumentUserViewRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.DcAutoForwardConfigService;
import lk.customs.rms.service.DocumentService;
import lk.customs.rms.service.PermissionService;
import lk.customs.rms.service.RealtimeNotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * ==========================================================
 * FILE: DocumentServiceImpl.java
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

    private final DocumentRepository documentRepository;
    private final DocumentAttachmentRepository attachmentRepository;
    private final DocumentMovementRepository movementRepository;
    private final DocumentRemarkRepository remarkRepository;
    private final DocumentUserViewRepository documentUserViewRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final PermissionService permissionService;
    private final RealtimeNotificationService realtimeNotificationService;
    private final DcAutoForwardConfigService dcAutoForwardConfigService;

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            DocumentAttachmentRepository attachmentRepository,
            DocumentMovementRepository movementRepository,
            DocumentRemarkRepository remarkRepository,
            DocumentUserViewRepository documentUserViewRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
                PermissionService permissionService,
                RealtimeNotificationService realtimeNotificationService,
                DcAutoForwardConfigService dcAutoForwardConfigService
    ) {
        this.documentRepository = documentRepository;
        this.attachmentRepository = attachmentRepository;
        this.movementRepository = movementRepository;
        this.remarkRepository = remarkRepository;
        this.documentUserViewRepository = documentUserViewRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.permissionService = permissionService;
        this.realtimeNotificationService = realtimeNotificationService;
        this.dcAutoForwardConfigService = dcAutoForwardConfigService;
    }

    // ==========================================================
    // DOCUMENT CRUD
    // ==========================================================

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
        doc.setVisibility("PRIVATE");

        // Document starts as PENDING
        doc.setStatus(Status.PENDING);

        doc.setCreatedByUserId(createdBy.getId());
        doc.setCurrentOwnerUserId(createdBy.getId());
        doc.setCreatedAt(LocalDateTime.now());
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
        movementRepository.save(mv);

        auditLogService.logDocumentCreate(saved.getId(), createdBy.getId(), "Document created");

        return DocumentResponse.from(saved, createdBy.getFullName(), owner.getFullName(), null, null, false);
    }

    @Override
    public Page<DocumentResponse> getDocuments(int page, int size, String search, Long actorUserId) {
        var pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        boolean canViewAll = permissionService.hasPermission(actorUserId, AppPermission.VIEW_ALL_DOCUMENTS);
        boolean canViewPublic = permissionService.hasPermission(actorUserId, AppPermission.VIEW_PUBLIC_DOCUMENT);
        boolean canViewPrivate = permissionService.hasPermission(actorUserId, AppPermission.VIEW_PRIVATE_DOCUMENT);
        boolean canViewOwnCreated = permissionService.hasPermission(actorUserId, AppPermission.VIEW_OWN_CREATED_DOCUMENTS);

        Page<Document> docs;
        if (canViewAll) {
            if (search == null || search.isBlank()) {
                docs = documentRepository.findAllNotDeleted(pageable);
            } else {
                docs = documentRepository.searchNotDeleted(search.trim(), pageable);
            }
        } else {
            if (search == null || search.isBlank()) {
                docs = documentRepository.findAccessibleNotDeleted(
                        actorUserId,
                        canViewPublic,
                        canViewPrivate,
                        canViewOwnCreated,
                        MovementActionType.FORWARD,
                        pageable
                );
            } else {
                docs = documentRepository.searchAccessibleNotDeleted(
                        search.trim(),
                        actorUserId,
                        canViewPublic,
                        canViewPrivate,
                        canViewOwnCreated,
                        MovementActionType.FORWARD,
                        pageable
                );
            }
        }

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
        Map<Long, String> latestRemarkPreviews = docIds.isEmpty()
            ? Map.of()
            : remarkRepository.findLatestByDocumentIdsWithUser(docIds)
                .stream()
                .collect(Collectors.toMap(
                    DocumentRemark::getDocumentId,
                    this::toRemarkPreview,
                    (first, second) -> first
                ));
        Map<Long, LocalDateTime> inboxReceivedAtByDoc = docIds.isEmpty()
            ? Map.of()
            : movementRepository.findLatestInboundByActorAndDocumentIds(
                    actorUserId,
                    docIds,
                    List.of(MovementActionType.CREATE, MovementActionType.FORWARD, MovementActionType.RETURN)
                )
                .stream()
                .collect(Collectors.toMap(
                    DocumentMovement::getDocumentId,
                    DocumentMovement::getActionAt,
                    (first, second) -> first
                ));

        return docs.map(d -> {
            String createdByName = userRepository.findById(d.getCreatedByUserId()).map(User::getFullName).orElse(null);
            String ownerName = userRepository.findById(d.getCurrentOwnerUserId()).map(User::getFullName).orElse(null);
            boolean viewedByMe = viewedDocIds.contains(d.getId());
            String latestRemarkPreview = canViewRemarks(d, actorUserId) ? latestRemarkPreviews.get(d.getId()) : null;
            return DocumentResponse.from(
                d,
                createdByName,
                ownerName,
                mainAttachmentTypes.get(d.getId()),
                latestRemarkPreview,
                viewedByMe,
                inboxReceivedAtByDoc.get(d.getId())
            );
        });
    }

    @Override
    public Page<SentMessageResponse> getSentMessages(int page, int size, String search, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.VIEW_SENT_MESSAGES, "You are not allowed to view sent messages.");

        var pageable = PageRequest.of(page, size);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase();

        List<DocumentMovement> actorMovements = movementRepository
            .findByActionTypeAndActionByUserIdOrderByActionAtDescIdDesc(MovementActionType.FORWARD, actorUserId);

        List<Long> docIds = actorMovements.stream().map(DocumentMovement::getDocumentId).distinct().toList();
        Map<Long, Document> docsById = docIds.isEmpty()
            ? Map.of()
            : documentRepository.findAllById(docIds)
                .stream()
                .filter(d -> !d.isDeleted())
                .collect(Collectors.toMap(Document::getId, d -> d));

        List<DocumentMovement> filteredMovements = actorMovements.stream()
            .filter(m -> docsById.containsKey(m.getDocumentId()))
            .filter(m -> {
                if (normalizedSearch.isEmpty()) return true;
                Document d = docsById.get(m.getDocumentId());
                String ref = d == null || d.getRefNo() == null ? "" : d.getRefNo().toLowerCase();
                String title = d == null || d.getTitle() == null ? "" : d.getTitle().toLowerCase();
                String company = d == null || d.getCompanyName() == null ? "" : d.getCompanyName().toLowerCase();
                return ref.contains(normalizedSearch)
                    || title.contains(normalizedSearch)
                    || company.contains(normalizedSearch);
            })
            .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filteredMovements.size());
        if (start > end) {
            start = end;
        }
        List<DocumentMovement> pageMovements = filteredMovements.subList(start, end);

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

        List<SentMessageResponse> sentRows = pageMovements.stream().map(movement -> {
            Document doc = docsById.get(movement.getDocumentId());
            String ownMinutePreview = toOwnSentMinutePreview(
                movement,
                inboundByDoc.getOrDefault(movement.getDocumentId(), List.of()),
                ownRemarksByDoc.getOrDefault(movement.getDocumentId(), List.of())
            );
            if (!canViewRemarks(doc, actorUserId)) {
                ownMinutePreview = null;
            }
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
                .latestRemarkPreview(ownMinutePreview)
                .sentAt(movement.getActionAt())
                .build();
            }).toList();

            return new PageImpl<>(sentRows, pageable, filteredMovements.size());
    }

    @Override
    public DocumentResponse getDocumentById(Long id, Long actorUserId) {
        Document d = requireDocument(id);
        User actor = requireUser(actorUserId);
        ensureCanViewDocument(d, actorUserId);
        markViewedByUser(d.getId(), actorUserId);
        markDcViewedIfNeeded(d, actor);

        String createdByName = userRepository.findById(d.getCreatedByUserId()).map(User::getFullName).orElse(null);
        String ownerName = userRepository.findById(d.getCurrentOwnerUserId()).map(User::getFullName).orElse(null);
        String mainAttachmentType = resolveMainAttachmentType(d.getId());
        String latestRemarkPreview = canViewRemarks(d, actorUserId)
            ? remarkRepository.findFirstByDocumentIdOrderByRemarkedAtDesc(d.getId())
                .map(this::toRemarkPreview)
                .orElse(null)
            : null;

        return DocumentResponse.from(d, createdByName, ownerName, mainAttachmentType, latestRemarkPreview, true);
    }

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
    public DocumentResponse updateDocument(Long id, UpdateDocumentRequest request, Long actorUserId) {
        Document d = requireDocument(id);

        permissionService.ensurePermission(actorUserId, AppPermission.EDIT_DOCUMENT_DETAILS, "You are not allowed to edit document details.");

        // Only the current owner can edit details
        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
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
        d.setPriority(request.getPriority());

        Document saved = documentRepository.save(d);

        auditLogService.logDocumentUpdate(saved.getId(), actorUserId, "Document updated");

        String createdByName = userRepository.findById(saved.getCreatedByUserId()).map(User::getFullName).orElse(null);
        String ownerName = userRepository.findById(saved.getCurrentOwnerUserId()).map(User::getFullName).orElse(null);
        String mainAttachmentType = resolveMainAttachmentType(saved.getId());

        String latestRemarkPreview = remarkRepository.findFirstByDocumentIdOrderByRemarkedAtDesc(saved.getId())
            .map(this::toRemarkPreview)
            .orElse(null);

        return DocumentResponse.from(saved, createdByName, ownerName, mainAttachmentType, latestRemarkPreview, true);
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
    public void deleteDocument(Long id, Long actorUserId) {
        Document d = requireDocument(id);

        d.setDeleted(true);
        d.setDeletedAt(LocalDateTime.now());
        d.setDeletedByUserId(actorUserId);
        documentRepository.save(d);

        auditLogService.logDocumentDelete(id, actorUserId, "Document deleted (soft)");
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
        User toUser = requireUser(request.getToUserId());

        // Ownership check
        if (!d.getCurrentOwnerUserId().equals(actionBy.getId())) {
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
        Long to = toUser.getId();

        d.setCurrentOwnerUserId(to);
        documentUserViewRepository.deleteByDocumentIdAndUserId(d.getId(), to);
        d.setVisibility(forwardVisibility);
        applyDcAutoForwardTrackingAfterOwnershipChange(d, toUser);
        d.setStatus(Status.IN_PROGRESS);
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, from, to, actionBy.getId(), MovementActionType.FORWARD, forwardVisibility);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actionBy.getId(), "FORWARD", "Forwarded to userId=" + to + " with visibility=" + forwardVisibility);
        realtimeNotificationService.notifyDocumentForwarded(
            to,
            d.getId(),
            d.getRefNo(),
            actionBy.getId(),
            actionBy.getFullName()
        );
    }

    @Override
    public void returns(Long documentId, ForwardReturnRequest request, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.RETURN_DOCUMENT, "You are not allowed to return documents.");

        Document d = requireDocument(documentId);

        // FINAL STATE LOCK
        ensureCanForwardOrReturn(d);

        User actionBy = requireUser(actorUserId);
        User toUser = requireUser(request.getToUserId());

        // Ownership check
        if (!d.getCurrentOwnerUserId().equals(actionBy.getId())) {
            throw new BadRequestException("Only the current owner can return this document.");
        }

        // Save remark BEFORE ownership change
        saveRemarkIfPresent(d, actionBy.getId(), request.getRemarkText(), "Remark added during return");

        Long from = d.getCurrentOwnerUserId();
        Long to = toUser.getId();

        d.setCurrentOwnerUserId(to);
        documentUserViewRepository.deleteByDocumentIdAndUserId(d.getId(), to);
        applyDcAutoForwardTrackingAfterOwnershipChange(d, toUser);
        d.setStatus(Status.RETURNED);
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, from, to, actionBy.getId(), MovementActionType.RETURN);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actionBy.getId(), "RETURN", "Returned to userId=" + to);
    }

    @Override
    public void approve(Long documentId, DecisionRequest request, Long actorUserId) {
        if (!dcAutoForwardConfigService.isApproveRejectButtonsEnabled()) {
            throw new BadRequestException("Approve action is disabled by admin workflow settings.");
        }
        permissionService.ensurePermission(actorUserId, AppPermission.APPROVE_DOCUMENT, "You are not allowed to approve documents.");

        Document d = requireDocument(documentId);

        // FINAL STATE + transition guard
        ensureCanApproveOrReject(d);

        // DC must be current owner
        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
            throw new BadRequestException("Only the current owner can approve this document.");
        }

        saveRemarkIfPresent(d, actorUserId, request.getRemarkText(), "Remark added during approve");

        d.setStatus(Status.APPROVED);
        d.setCompletedAt(LocalDateTime.now());
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, d.getCurrentOwnerUserId(), null, actorUserId, MovementActionType.APPROVE);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actorUserId, "APPROVE", "Approved by DC");
    }

    @Override
    public void reject(Long documentId, DecisionRequest request, Long actorUserId) {
        if (!dcAutoForwardConfigService.isApproveRejectButtonsEnabled()) {
            throw new BadRequestException("Reject action is disabled by admin workflow settings.");
        }
        permissionService.ensurePermission(actorUserId, AppPermission.REJECT_DOCUMENT, "You are not allowed to reject documents.");

        Document d = requireDocument(documentId);

        // FINAL STATE + transition guard
        ensureCanApproveOrReject(d);

        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
            throw new BadRequestException("Only the current owner can reject this document.");
        }

        saveRemarkIfPresent(d, actorUserId, request.getRemarkText(), "Remark added during reject");

        d.setStatus(Status.REJECTED);
        d.setCompletedAt(LocalDateTime.now());
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, d.getCurrentOwnerUserId(), null, actorUserId, MovementActionType.REJECT);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actorUserId, "REJECT", "Rejected by DC");
    }

    @Override
    public void issue(Long documentId, DecisionRequest request, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.ISSUE_DOCUMENT, "You are not allowed to complete documents.");

        Document d = requireDocument(documentId);

        // ISSUE must happen ONLY after APPROVED
        ensureCanIssue(d);

        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
            throw new BadRequestException("Only the current owner can complete this document.");
        }

        saveRemarkIfPresent(d, actorUserId, request.getRemarkText(), "Remark added during issue");

        d.setStatus(Status.ISSUED);
        d.setIssuedAt(LocalDateTime.now());
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, d.getCurrentOwnerUserId(), null, actorUserId, MovementActionType.ISSUE);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actorUserId, "ISSUE", "Issued by DC");
    }

    // ==========================================================
    // NEW: REOPEN
    // ==========================================================

    @Override
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

        // DC must also be the current owner (strong integrity)
        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
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

        documentRepository.save(d);

        // Movement: REOPEN (owner unchanged; log from->to as same owner)
        Long owner = d.getCurrentOwnerUserId();
        DocumentMovement mv = DocumentMovement.create(documentId, owner, owner, actorUserId, MovementActionType.REOPEN);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actorUserId, "REOPEN", "Reopened by DC");
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

    // ==========================================================
    // REMARK HELPER
    // ==========================================================

    private void saveRemarkIfPresent(Document doc, Long actionByUserId, String remarkText, String auditMessage) {
        if (remarkText == null) return;

        permissionService.ensurePermission(actionByUserId, AppPermission.ADD_REMARK, "You are not allowed to add minutes.");

        String text = remarkText.trim();
        if (text.isEmpty()) return;

        // Only current owner can add remark
        if (!doc.getCurrentOwnerUserId().equals(actionByUserId)) {
            throw new BadRequestException("Only the current owner can add remarks.");
        }

        DocumentRemark remark = DocumentRemark.builder()
                .documentId(doc.getId())
                .remarkText(text)
                .remarkedByUserId(actionByUserId)
                .remarkedAt(LocalDateTime.now())
                .build();

        DocumentRemark saved = remarkRepository.save(remark);

        auditLogService.logRemark(doc.getId(), actionByUserId, "REMARK", auditMessage, saved.getId());
    }

    // ==========================================================
    // COMMON HELPERS
    // ==========================================================

    private Document requireDocument(Long documentId) {
        return documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
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

        if (actorUserId.equals(doc.getCurrentOwnerUserId())) {
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
            return "PUBLIC";
        }
        return value.trim().toUpperCase();
    }

    private void applyDcAutoForwardTrackingAfterOwnershipChange(Document doc, User toUser) {
        if (isRole(toUser, "DC")) {
            doc.setDcAssignedAt(LocalDateTime.now());
            doc.setDcViewedAt(null);
            return;
        }

        doc.setDcAssignedAt(null);
        doc.setDcViewedAt(null);
    }

    private void markDcViewedIfNeeded(Document doc, User actor) {
        if (!isRole(actor, "DC")) return;
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
        if (document.getCurrentOwnerUserId() != null && document.getCurrentOwnerUserId().equals(actorUserId)) {
            return true;
        }
        return permissionService.hasPermission(actorUserId, AppPermission.VIEW_REMARKS_WHEN_NOT_REPORT_AT);
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
