package lk.customs.rms.service;

import lk.customs.rms.dto.CreateDocumentRequest;
import lk.customs.rms.dto.DocumentResponse;
import lk.customs.rms.dto.MyWorkloadStatsResponse;
import lk.customs.rms.dto.SentMessageResponse;
import lk.customs.rms.dto.UpdateDocumentRequest;
import lk.customs.rms.dto.UpdateDocumentRecipientsRequest;
import lk.customs.rms.dto.DecisionRequest;
import lk.customs.rms.dto.ForwardReturnRequest;
import lk.customs.rms.dto.UndoSendRequest;
import lk.customs.rms.enums.RecipientType;
import org.springframework.data.domain.Page;

public interface DocumentService {

    DocumentResponse createDocument(CreateDocumentRequest request, Long actorUserId);

    Page<DocumentResponse> getDocuments(int page, int size, String search, String status, String priority,
                                        String receivedFrom, String receivedTo, String sort, Long actorUserId);

    Page<DocumentResponse> getMyInboxDocuments(int page, int size, String search, String status, String priority,
                                               String sort, RecipientType recipientType, Long actorUserId);

    Page<SentMessageResponse> getSentMessages(int page, int size, String search, String status, String priority, Long actorUserId);

    DocumentResponse getDocumentById(Long id, Long actorUserId);

    MyWorkloadStatsResponse getMyWorkloadStats(Long actorUserId);

    DocumentResponse updateDocument(Long id, UpdateDocumentRequest request, Long actorUserId);

    DocumentResponse updateRecipients(Long id, UpdateDocumentRecipientsRequest request, Long actorUserId);

    void deleteDocument(Long id, Long actorUserId);

    // workflow
    void forward(Long documentId, ForwardReturnRequest request, Long actorUserId);

    void returns(Long documentId, ForwardReturnRequest request, Long actorUserId);

    void undoSend(Long documentId, UndoSendRequest request, Long actorUserId);

    void approve(Long documentId, DecisionRequest request, Long actorUserId);

    void reject(Long documentId, DecisionRequest request, Long actorUserId);

    void issue(Long documentId, DecisionRequest request, Long actorUserId);

    /**
     * NEW: REOPEN (controlled)
     * Only users with reopen permission can reopen a document that is APPROVED or REJECTED.
     * Not allowed for ISSUED.
     * Requires a reason (remarkText must not be empty).
     */
    void reopen(Long documentId, DecisionRequest request, Long actorUserId);
}
