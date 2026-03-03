package lk.customs.rms.controller;

import lk.customs.rms.dto.MovementResponse;
import lk.customs.rms.repository.DocumentMovementRepository;
import lk.customs.rms.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/documents/{documentId}/movements")
public class DocumentMovementController {

    private final DocumentMovementRepository movementRepository;
    private final UserRepository userRepository;

    public DocumentMovementController(DocumentMovementRepository movementRepository, UserRepository userRepository) {
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<MovementResponse> getMovements(@PathVariable Long documentId) {
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
                        .actionByUserId(m.getActionByUserId())
                        .actionByUserName(userRepository.findById(m.getActionByUserId()).map(u -> u.getFullName()).orElse(null))
                        .actionAt(m.getActionAt())
                        .build())
                .toList();
    }
}
