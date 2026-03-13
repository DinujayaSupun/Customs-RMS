package lk.customs.rms.entity;

import jakarta.persistence.*;
import lk.customs.rms.enums.MovementActionType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_movements")
@Getter
@Setter
public class DocumentMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="document_id", nullable = false)
    private Long documentId;

    @Enumerated(EnumType.STRING)
    @Column(name="action_type", nullable = false)
    private MovementActionType actionType;

    @Column(name="from_user_id")
    private Long fromUserId;

    @Column(name="to_user_id")
    private Long toUserId;

    @Column(name="action_by_user_id", nullable = false)
    private Long actionByUserId;

    @Column(name="action_at", nullable = false)
    private LocalDateTime actionAt;

    @Column(name = "forward_visibility", length = 10)
    private String forwardVisibility;

    public static DocumentMovement create(Long docId, Long from, Long to, Long by, MovementActionType type) {
        return create(docId, from, to, by, type, null);
    }

    public static DocumentMovement create(Long docId, Long from, Long to, Long by, MovementActionType type, String forwardVisibility) {
        DocumentMovement m = new DocumentMovement();
        m.setDocumentId(docId);
        m.setFromUserId(from);
        m.setToUserId(to);
        m.setActionByUserId(by);
        m.setActionType(type);
        m.setActionAt(LocalDateTime.now());
        m.setForwardVisibility(forwardVisibility);
        return m;
    }
}
