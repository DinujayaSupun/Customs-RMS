package lk.customs.rms.service;

import lk.customs.rms.dto.CreateDocumentRequest;
import lk.customs.rms.dto.DocumentResponse;
import lk.customs.rms.dto.UpdateDocumentRequest;
import lk.customs.rms.dto.DecisionRequest;
import lk.customs.rms.dto.ForwardReturnRequest;
import org.springframework.data.domain.Page;

public interface DocumentService {

    DocumentResponse createDocument(CreateDocumentRequest request, Long actorUserId);

    Page<DocumentResponse> getDocuments(int page, int size, String search);

    DocumentResponse getDocumentById(Long id);

    DocumentResponse updateDocument(Long id, UpdateDocumentRequest request, Long actorUserId);

    void deleteDocument(Long id, Long actorUserId);

    // workflow
    void forward(Long documentId, ForwardReturnRequest request, Long actorUserId);

    void returns(Long documentId, ForwardReturnRequest request, Long actorUserId);

    void approve(Long documentId, DecisionRequest request, Long actorUserId);

    void reject(Long documentId, DecisionRequest request, Long actorUserId);

    void issue(Long documentId, DecisionRequest request, Long actorUserId);

    /**
     * NEW: REOPEN (controlled)
     * Only DC can reopen a document that is APPROVED or REJECTED.
     * Not allowed for ISSUED.
     * Requires a reason (remarkText must not be empty).
     */
    void reopen(Long documentId, DecisionRequest request, Long actorUserId);
}
