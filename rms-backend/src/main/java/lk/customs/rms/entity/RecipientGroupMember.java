package lk.customs.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Membership of a user in a {@link RecipientGroup}. {@code isAdmin} splits admins from regular members. */
@Entity
@Table(
        name = "recipient_group_member",
        uniqueConstraints = @UniqueConstraint(name = "uk_recipient_group_member", columnNames = {"group_id", "user_id"}),
        indexes = @Index(name = "idx_recipient_group_member_group", columnList = "group_id")
)
@Getter
@Setter
public class RecipientGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipient_group_member_id")
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "is_admin", nullable = false)
    private Boolean isAdmin = false;
}
