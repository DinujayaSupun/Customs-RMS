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

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
