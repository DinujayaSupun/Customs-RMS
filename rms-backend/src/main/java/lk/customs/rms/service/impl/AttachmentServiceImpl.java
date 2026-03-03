package lk.customs.rms.service.impl;

import lk.customs.rms.dto.AttachmentDownloadResult;
import lk.customs.rms.dto.AttachmentResponse;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentAttachment;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.exception.ResourceNotFoundException;
import lk.customs.rms.repository.DocumentAttachmentRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.AttachmentService;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.FileStorageService;
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

    public AttachmentServiceImpl(
            DocumentRepository documentRepository,
            DocumentAttachmentRepository attachmentRepository,
            UserRepository userRepository,
            FileStorageService fileStorageService,
            AuditLogService auditLogService
    ) {
        this.documentRepository = documentRepository;
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public AttachmentResponse upload(Long documentId, Long uploadedByUserId, MultipartFile file) {
        Document doc = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        var uploader = userRepository.findById(uploadedByUserId)
                .orElseThrow(() -> new BadRequestException("User not found: " + uploadedByUserId));

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

        auditLogService.logAttachment(documentId, saved.getId(), uploadedByUserId, "UPLOAD",
                "Attachment uploaded: v" + nextVersion + " " + saved.getFileName());

        return toResponse(saved);
    }

    @Override
    public List<AttachmentResponse> listForDocument(Long documentId) {
        documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        return attachmentRepository.findByDocumentIdAndDeletedFalseOrderByVersionNoAsc(documentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public AttachmentDownloadResult downloadWithMeta(Long attachmentId, Long performedByUserId) {
        DocumentAttachment a = attachmentRepository.findByIdAndDeletedFalse(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));

        Resource resource = fileStorageService.loadAsResource(a.getFilePath());

        // ✅ Optional audit log for download (only if caller gives performedByUserId)
        if (performedByUserId != null) {
            // validate user exists
            userRepository.findById(performedByUserId)
                    .orElseThrow(() -> new BadRequestException("User not found: " + performedByUserId));

            auditLogService.logAttachment(a.getDocumentId(), a.getId(), performedByUserId, "DOWNLOAD",
                    "Attachment downloaded: v" + a.getVersionNo() + " " + a.getFileName());
        }

        return new AttachmentDownloadResult(resource, a.getFileName());
    }

    @Override
    @Transactional
    public void softDelete(Long attachmentId, Long performedByUserId) {
        DocumentAttachment a = attachmentRepository.findByIdAndDeletedFalse(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));

        userRepository.findById(performedByUserId)
                .orElseThrow(() -> new BadRequestException("User not found: " + performedByUserId));

        a.setDeleted(true);
        a.setDeletedAt(LocalDateTime.now());
        a.setDeletedBy(performedByUserId);
        a.setIsLatest(false);
        attachmentRepository.save(a);

        // Restore latest to previous highest version (if any)
        var remaining = attachmentRepository.findByDocumentIdAndDeletedFalseOrderByVersionNoDesc(a.getDocumentId());
        if (!remaining.isEmpty()) {
            DocumentAttachment latest = remaining.get(0);
            latest.setIsLatest(true);
            attachmentRepository.save(latest);
        }

        auditLogService.logAttachment(a.getDocumentId(), a.getId(), performedByUserId, "DELETE",
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
}
