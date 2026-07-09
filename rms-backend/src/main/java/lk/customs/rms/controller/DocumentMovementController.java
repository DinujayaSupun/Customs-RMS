package lk.customs.rms.controller;

import lk.customs.rms.dto.MovementResponse;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentMovement;
import lk.customs.rms.entity.RecipientGroup;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.exception.ResourceNotFoundException;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.DocumentMovementRepository;
import lk.customs.rms.repository.RecipientGroupRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.PermissionService;
import lk.customs.rms.service.DocumentRecipientService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController

@RequestMapping("/api/documents/{documentId}/movements")
public class DocumentMovementController {

        private final DocumentRepository documentRepository;
    private final DocumentMovementRepository movementRepository;
    private final UserRepository userRepository;
    private final RecipientGroupRepository recipientGroupRepository;
        private final CurrentUserService currentUserService;
        private final PermissionService permissionService;
        private final DocumentRecipientService documentRecipientService;

        public DocumentMovementController(DocumentRepository documentRepository,
                                                                          DocumentMovementRepository movementRepository,
                                                                          UserRepository userRepository,
                                                                          RecipientGroupRepository recipientGroupRepository,
                                                                          CurrentUserService currentUserService,
                                                                          PermissionService permissionService,
                                                                          DocumentRecipientService documentRecipientService) {
                this.documentRepository = documentRepository;
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
        this.recipientGroupRepository = recipientGroupRepository;
                this.currentUserService = currentUserService;
                this.permissionService = permissionService;
                this.documentRecipientService = documentRecipientService;
    }

    @GetMapping
        public List<MovementResponse> getMovements(@PathVariable Long documentId, Authentication authentication) {
                Document doc = documentRepository.findByIdAndDeletedFalse(documentId)
                                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

                Long actorUserId = currentUserService.requireUserId(authentication);
                if (!documentRecipientService.canViewTimeline(doc, actorUserId)) {
                        throw new BadRequestException("You are not allowed to view movement history for this document.");
                }

        List<DocumentMovement> movements = movementRepository.findByDocumentIdOrderByActionAtAsc(documentId);
        Map<Long, User> usersById = usersByIdForMovements(movements);
        Map<Long, String> groupNamesById = groupNamesForMovements(movements);

        return movements
                .stream()
                .map(m -> MovementResponse.builder()
                        .id(m.getId())
                        .documentId(m.getDocumentId())
                        .actionType(m.getActionType())
                        .fromUserId(m.getFromUserId())
                        .fromUserName(userName(usersById, m.getFromUserId()))
                        .toUserId(m.getToUserId())
                        .toUserName(userName(usersById, m.getToUserId()))
                        .toGroupId(m.getToGroupId())
                        .toGroupName(groupNamesById.get(m.getToGroupId()))
                        .forwardVisibility(m.getForwardVisibility())
                        .recipientSummary(documentRecipientService.summaryForMovement(doc, m.getId(), actorUserId))
                        .actionByUserId(m.getActionByUserId())
                        .actionByUserName(userName(usersById, m.getActionByUserId()))
                        .actionAt(m.getActionAt())
                        .build())
                .toList();
    }

    private Map<Long, String> groupNamesForMovements(List<DocumentMovement> movements) {
        Set<Long> groupIds = movements.stream()
                .map(DocumentMovement::getToGroupId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        // Not Map.of(): almost every movement has a null toGroupId, and Map.of().get(null) throws
        // (unlike HashMap/Collections.emptyMap(), which return null for a missing/null key).
        if (groupIds.isEmpty()) return java.util.Collections.emptyMap();

        return recipientGroupRepository.findAllById(groupIds)
                .stream()
                .collect(Collectors.toMap(RecipientGroup::getId, RecipientGroup::getName));
    }

    private Map<Long, User> usersByIdForMovements(List<DocumentMovement> movements) {
        Set<Long> userIds = new HashSet<>();
        for (DocumentMovement movement : movements) {
            addIfPresent(userIds, movement.getFromUserId());
            addIfPresent(userIds, movement.getToUserId());
            addIfPresent(userIds, movement.getActionByUserId());
        }

        if (userIds.isEmpty()) return Map.of();

        return userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private void addIfPresent(Set<Long> userIds, Long userId) {
        if (userId != null) userIds.add(userId);
    }

    private String userName(Map<Long, User> usersById, Long userId) {
        User user = userId == null ? null : usersById.get(userId);
        return user == null ? null : user.getFullName();
    }
}
