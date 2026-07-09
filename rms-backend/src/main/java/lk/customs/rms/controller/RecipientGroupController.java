package lk.customs.rms.controller;

import jakarta.validation.Valid;
import lk.customs.rms.dto.GroupHeldDocumentResponse;
import lk.customs.rms.dto.RecipientGroupResponse;
import lk.customs.rms.dto.SaveRecipientGroupRequest;
import lk.customs.rms.entity.User;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.RecipientGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class RecipientGroupController {

    private final RecipientGroupService recipientGroupService;
    private final CurrentUserService currentUserService;

    public RecipientGroupController(RecipientGroupService recipientGroupService,
                                    CurrentUserService currentUserService) {
        this.recipientGroupService = recipientGroupService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<RecipientGroupResponse> list() {
        return recipientGroupService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecipientGroupResponse create(@Valid @RequestBody SaveRecipientGroupRequest request,
                                         Authentication authentication) {
        User actor = currentUserService.requireUser(authentication);
        return recipientGroupService.create(request, actor);
    }

    @PutMapping("/{id}")
    public RecipientGroupResponse update(@PathVariable("id") Long id,
                                         @Valid @RequestBody SaveRecipientGroupRequest request,
                                         Authentication authentication) {
        User actor = currentUserService.requireUser(authentication);
        return recipientGroupService.update(id, request, actor);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id, Authentication authentication) {
        User actor = currentUserService.requireUser(authentication);
        recipientGroupService.delete(id, actor);
    }

    @GetMapping("/{id}/documents")
    public List<GroupHeldDocumentResponse> documents(@PathVariable("id") Long id) {
        return recipientGroupService.documentsHeldBy(id);
    }
}
