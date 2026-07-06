package lk.customs.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-DC auto-forward receiver mapping: when a document owned by {@code dcUserId} (a DC) times
 * out, it auto-forwards to {@code receiverUserId} (a DDC/SDDC). One row per DC. A DC with no row
 * here is skipped by the scheduler (no receiver configured for that DC).
 */
@Entity
@Table(
        name = "dc_auto_forward_receiver",
        uniqueConstraints = @UniqueConstraint(name = "uk_dc_auto_forward_receiver_dc_user", columnNames = {"dc_user_id"})
)
@Getter
@Setter
public class DcAutoForwardReceiver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dc_auto_forward_receiver_id")
    private Long id;

    @Column(name = "dc_user_id", nullable = false)
    private Long dcUserId;

    @Column(name = "receiver_user_id", nullable = false)
    private Long receiverUserId;
}
