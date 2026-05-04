package lk.customs.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "dc_auto_forward_config")
@Getter
@Setter
public class DcAutoForwardConfig {

    @Id
    @Column(name = "config_id")
    private Long id = 1L;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "timeout_minutes", nullable = false)
    private Integer timeoutMinutes = 60;

    @Column(name = "receiver_user_id")
    private Long receiverUserId;

    @Column(name = "forward_return_allowed_statuses", nullable = false, length = 200)
    private String forwardReturnAllowedStatuses = "PENDING,IN_PROGRESS,RETURNED";

    @Column(name = "approve_reject_buttons_enabled", nullable = false)
    private boolean approveRejectButtonsEnabled = true;

    @Column(name = "undo_send_enabled", nullable = false)
    private boolean undoSendEnabled = true;

    @Column(name = "undo_send_window_hours", nullable = false)
    private Integer undoSendWindowHours = 24;

    @Column(name = "undo_send_requires_unopened", nullable = false)
    private boolean undoSendRequiresUnopened = true;

    @Column(name = "undo_send_allowed_actions", nullable = false, length = 80)
    private String undoSendAllowedActions = "FORWARD,RETURN";

    @Column(name = "undo_send_requires_reason", nullable = false)
    private boolean undoSendRequiresReason = true;

    @Column(name = "undo_send_notify_receiver", nullable = false)
    private boolean undoSendNotifyReceiver = true;

    @Column(name = "undo_send_show_expired_info", nullable = false)
    private boolean undoSendShowExpiredInfo = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
