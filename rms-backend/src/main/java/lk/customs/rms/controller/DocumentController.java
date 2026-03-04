package lk.customs.rms.controller;

import jakarta.validation.Valid;
import lk.customs.rms.dto.*;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.DocumentService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/documents", "/api/reports"})
@CrossOrigin
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUserService currentUserService;

    public DocumentController(DocumentService documentService, CurrentUserService currentUserService) {
        this.documentService = documentService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse create(@Valid @RequestBody CreateDocumentRequest request, Authentication authentication) {
        return documentService.createDocument(request, currentUserService.requireUserId(authentication));
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
    public DocumentResponse update(@PathVariable Long id,
                                   @Valid @RequestBody UpdateDocumentRequest request,
                                   Authentication authentication) {
        return documentService.updateDocument(id, request, currentUserService.requireUserId(authentication));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        documentService.deleteDocument(id, currentUserService.requireUserId(authentication));
    }

    // workflow
    @PostMapping("/{id}/forward")
    public void forward(@PathVariable Long id,
                        @Valid @RequestBody ForwardReturnRequest request,
                        Authentication authentication) {
        documentService.forward(id, request, currentUserService.requireUserId(authentication));
    }

    @PostMapping("/{id}/return")
    public void returns(@PathVariable Long id,
                        @Valid @RequestBody ForwardReturnRequest request,
                        Authentication authentication) {
        documentService.returns(id, request, currentUserService.requireUserId(authentication));
    }

    @PostMapping("/{id}/approve")
    public void approve(@PathVariable Long id,
                        @Valid @RequestBody DecisionRequest request,
                        Authentication authentication) {
        documentService.approve(id, request, currentUserService.requireUserId(authentication));
    }

    @PostMapping("/{id}/reject")
    public void reject(@PathVariable Long id,
                       @Valid @RequestBody DecisionRequest request,
                       Authentication authentication) {
        documentService.reject(id, request, currentUserService.requireUserId(authentication));
    }

    @PostMapping("/{id}/issue")
    public void issue(@PathVariable Long id,
                      @Valid @RequestBody DecisionRequest request,
                      Authentication authentication) {
        documentService.issue(id, request, currentUserService.requireUserId(authentication));
    }

    /**
     * NEW: REOPEN endpoint
     * Works for both:
     * - /api/documents/{id}/reopen
     * - /api/reports/{id}/reopen
     */
    @PostMapping("/{id}/reopen")
    public void reopen(@PathVariable Long id,
                       @Valid @RequestBody DecisionRequest request,
                       Authentication authentication) {
        documentService.reopen(id, request, currentUserService.requireUserId(authentication));
    }
}
