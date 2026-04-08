package lk.customs.rms.controller;

import lk.customs.rms.dto.MovementResponse;
import lk.customs.rms.entity.Document;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.exception.ResourceNotFoundException;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.DocumentMovementRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.PermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/documents/{documentId}/movements")
public class DocumentMovementController {

        private final DocumentRepository documentRepository;
    private final DocumentMovementRepository movementRepository;
    private final UserRepository userRepository;
        private final CurrentUserService currentUserService;
        private final PermissionService permissionService;

        public DocumentMovementController(DocumentRepository documentRepository,
                                                                          DocumentMovementRepository movementRepository,
                                                                          UserRepository userRepository,
                                                                          CurrentUserService currentUserService,
                                                                          PermissionService permissionService) {
                this.documentRepository = documentRepository;
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
                this.currentUserService = currentUserService;
                this.permissionService = permissionService;
    }

    @GetMapping
        public List<MovementResponse> getMovements(@PathVariable Long documentId, Authentication authentication) {
                Document doc = documentRepository.findByIdAndDeletedFalse(documentId)
                                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

                Long actorUserId = currentUserService.requireUserId(authentication);
                if (!doc.getCurrentOwnerUserId().equals(actorUserId)
                                && !permissionService.hasPermission(actorUserId, AppPermission.VIEW_ALL_HISTORY)) {
                        throw new BadRequestException("You are not allowed to view movement history for this document.");
                }

        return movementRepository.findByDocumentIdOrderByActionAtAsc(documentId)
                .stream()
                .map(m -> MovementResponse.builder()
                        .id(m.getId())
                        .documentId(m.getDocumentId())
                        .actionType(m.getActionType())
                        .fromUserId(m.getFromUserId())
                        .fromUserName(m.getFromUserId() == null ? null :
                                userRepository.findById(m.getFromUserId()).map(u -> u.getFullName()).orElse(null))
                        .toUserId(m.getToUserId())
                        .toUserName(m.getToUserId() == null ? null :
                                userRepository.findById(m.getToUserId()).map(u -> u.getFullName()).orElse(null))
                        .forwardVisibility(m.getForwardVisibility())
                        .actionByUserId(m.getActionByUserId())
                        .actionByUserName(userRepository.findById(m.getActionByUserId()).map(u -> u.getFullName()).orElse(null))
                        .actionAt(m.getActionAt())
                        .build())
                .toList();
    }
}
