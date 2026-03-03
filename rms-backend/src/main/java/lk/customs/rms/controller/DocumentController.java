package lk.customs.rms.controller;

import jakarta.validation.Valid;
import lk.customs.rms.dto.*;
import lk.customs.rms.service.DocumentService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/documents", "/api/reports"})
@CrossOrigin
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse create(@Valid @RequestBody CreateDocumentRequest request) {
        return documentService.createDocument(request);
    }

    @GetMapping
    public Page<DocumentResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search
    ) {
        return documentService.getDocuments(page, size, search);
    }

    @GetMapping("/{id}")
    public DocumentResponse getById(@PathVariable Long id) {
        return documentService.getDocumentById(id);
    }

    @PutMapping("/{id}")
    public DocumentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDocumentRequest request) {
        return documentService.updateDocument(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @RequestParam Long performedByUserId) {
        documentService.deleteDocument(id, performedByUserId);
    }

    // workflow
    @PostMapping("/{id}/forward")
    public void forward(@PathVariable Long id, @Valid @RequestBody ForwardReturnRequest request) {
        documentService.forward(id, request);
    }

    @PostMapping("/{id}/return")
    public void returns(@PathVariable Long id, @Valid @RequestBody ForwardReturnRequest request) {
        documentService.returns(id, request);
    }

    @PostMapping("/{id}/approve")
    public void approve(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        documentService.approve(id, request);
    }

    @PostMapping("/{id}/reject")
    public void reject(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        documentService.reject(id, request);
    }

    @PostMapping("/{id}/issue")
    public void issue(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        documentService.issue(id, request);
    }

    /**
     * NEW: REOPEN endpoint
     * Works for both:
     * - /api/documents/{id}/reopen
     * - /api/reports/{id}/reopen
     */
    @PostMapping("/{id}/reopen")
    public void reopen(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        documentService.reopen(id, request);
    }
}
