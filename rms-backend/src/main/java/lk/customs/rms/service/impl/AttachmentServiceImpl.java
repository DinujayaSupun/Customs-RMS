package lk.customs.rms.service.impl;

import lk.customs.rms.dto.AttachmentDownloadResult;
import lk.customs.rms.dto.AttachmentResponse;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentAttachment;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.exception.ResourceNotFoundException;
import lk.customs.rms.repository.DocumentAttachmentRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.AttachmentService;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.FileStorageService;
import lk.customs.rms.service.PermissionService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AttachmentServiceImpl implements AttachmentService {

    private final DocumentRepository documentRepository;
    private final DocumentAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
        private final PermissionService permissionService;

    public AttachmentServiceImpl(
            DocumentRepository documentRepository,
            DocumentAttachmentRepository attachmentRepository,
            UserRepository userRepository,
            FileStorageService fileStorageService,
                        AuditLogService auditLogService,
                        PermissionService permissionService
    ) {
        this.documentRepository = documentRepository;
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
                this.permissionService = permissionService;
    }

    @Override
    @Transactional
        public AttachmentResponse upload(Long documentId, Long actorUserId, MultipartFile file) {
        Document doc = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

                User uploader = requireUser(actorUserId);
                permissionService.ensurePermission(actorUserId, AppPermission.UPLOAD_ATTACHMENT, "You are not allowed to upload attachments.");

                if (!doc.getCurrentOwnerUserId().equals(actorUserId)) {
                        throw new BadRequestException("Only the current owner can upload attachments.");
                }

        int nextVersion = attachmentRepository.findMaxVersionNo(documentId) + 1;

        // Mark current latest=false (if any)
        var existing = attachmentRepository.findByDocumentIdAndDeletedFalseOrderByVersionNoDesc(documentId);
        for (var a : existing) {
            if (Boolean.TRUE.equals(a.getIsLatest())) {
                a.setIsLatest(false);
                attachmentRepository.save(a);
            }
        }

        String relativePath = fileStorageService.saveDocumentAttachment(documentId, nextVersion, file);

        DocumentAttachment a = new DocumentAttachment();
        a.setDocumentId(doc.getId());
        a.setFileName(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        a.setFilePath(relativePath);
        a.setVersionNo(nextVersion);
        a.setIsLatest(true);
        a.setUploadedBy(uploader.getId());
        a.setUploadedAt(LocalDateTime.now());
        a.setDeleted(false);

        DocumentAttachment saved = attachmentRepository.save(a);

        auditLogService.logAttachment(documentId, saved.getId(), actorUserId, "UPLOAD",
                "Attachment uploaded: v" + nextVersion + " " + saved.getFileName());

        return toResponse(saved);
    }

    @Override
        public List<AttachmentResponse> listForDocument(Long documentId, Long actorUserId) {
                Document doc = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

                ensureHistoryAccess(doc, actorUserId);

        return attachmentRepository.findByDocumentIdAndDeletedFalseOrderByVersionNoAsc(documentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
        public AttachmentDownloadResult downloadWithMeta(Long attachmentId, Long actorUserId) {
        DocumentAttachment a = attachmentRepository.findByIdAndDeletedFalse(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));

                Document doc = documentRepository.findByIdAndDeletedFalse(a.getDocumentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + a.getDocumentId()));

                ensureHistoryAccess(doc, actorUserId);

        Resource resource = fileStorageService.loadAsResource(a.getFilePath());

        // ✅ Optional audit log for download (only if caller gives performedByUserId)
        if (actorUserId != null) {
            // validate user exists
                    requireUser(actorUserId);

            auditLogService.logAttachment(a.getDocumentId(), a.getId(), actorUserId, "DOWNLOAD",
                    "Attachment downloaded: v" + a.getVersionNo() + " " + a.getFileName());
        }

        return new AttachmentDownloadResult(resource, a.getFileName());
    }

    @Override
    @Transactional
        public void softDelete(Long attachmentId, Long actorUserId) {
        DocumentAttachment a = attachmentRepository.findByIdAndDeletedFalse(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));

                Document doc = documentRepository.findByIdAndDeletedFalse(a.getDocumentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + a.getDocumentId()));

                requireUser(actorUserId);
                permissionService.ensurePermission(actorUserId, AppPermission.DELETE_ATTACHMENT, "You are not allowed to delete attachments.");

                if (!doc.getCurrentOwnerUserId().equals(actorUserId)) {
                    throw new BadRequestException("Only the current owner can delete attachments.");
                }

        a.setDeleted(true);
        a.setDeletedAt(LocalDateTime.now());
        a.setDeletedBy(actorUserId);
        a.setIsLatest(false);
        attachmentRepository.save(a);

        // Restore latest to previous highest version (if any)
        var remaining = attachmentRepository.findByDocumentIdAndDeletedFalseOrderByVersionNoDesc(a.getDocumentId());
        if (!remaining.isEmpty()) {
            DocumentAttachment latest = remaining.get(0);
            latest.setIsLatest(true);
            attachmentRepository.save(latest);
        }

        auditLogService.logAttachment(a.getDocumentId(), a.getId(), actorUserId, "DELETE",
                "Attachment soft-deleted: v" + a.getVersionNo() + " " + a.getFileName());
    }

    private AttachmentResponse toResponse(DocumentAttachment a) {
        String uploadedByName = userRepository.findById(a.getUploadedBy()).map(u -> u.getFullName()).orElse(null);
        String deletedByName = a.getDeletedBy() == null ? null :
                userRepository.findById(a.getDeletedBy()).map(u -> u.getFullName()).orElse(null);

        return AttachmentResponse.builder()
                .id(a.getId())
                .documentId(a.getDocumentId())
                .fileName(a.getFileName())
                .filePath(a.getFilePath())
                .versionNo(a.getVersionNo())
                .isLatest(a.getIsLatest())
                .uploadedBy(a.getUploadedBy())
                .uploadedByName(uploadedByName)
                .uploadedAt(a.getUploadedAt())
                .isDeleted(a.getDeleted())
                .deletedAt(a.getDeletedAt())
                .deletedBy(a.getDeletedBy())
                .deletedByName(deletedByName)
                .build();
    }

        private User requireUser(Long userId) {
                return userRepository.findById(userId)
                                .orElseThrow(() -> new BadRequestException("User not found: " + userId));
        }

        private void ensureHistoryAccess(Document doc, Long actorUserId) {
                if (doc.getCurrentOwnerUserId().equals(actorUserId)) {
                        return;
                }

                if (permissionService.hasPermission(actorUserId, AppPermission.VIEW_ALL_HISTORY)) {
                        return;
                }

                throw new BadRequestException("You are not allowed to view file history for this document.");
        }
}
