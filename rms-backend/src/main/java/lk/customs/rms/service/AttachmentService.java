package lk.customs.rms.service;

import lk.customs.rms.dto.AttachmentDownloadResult;
import lk.customs.rms.dto.AttachmentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AttachmentService {

    AttachmentResponse upload(Long documentId, Long uploadedByUserId, MultipartFile file);

    List<AttachmentResponse> listForDocument(Long documentId);

    // ✅ download with filename (optional audit logging using performedByUserId)
    AttachmentDownloadResult downloadWithMeta(Long attachmentId, Long performedByUserId);

    void softDelete(Long attachmentId, Long performedByUserId);
}
