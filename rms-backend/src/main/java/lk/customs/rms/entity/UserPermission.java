package lk.customs.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-user permission override on top of the user's role.
 * A row means: enabled=true → GRANT this permission regardless of the role; enabled=false → REVOKE
 * it regardless of the role. No row for a (user, permission) pair means "inherit the role's value".
 */
@Entity
@Table(
        name = "user_permissions",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_permissions_user_permission", columnNames = {"user_id", "permission_name"}),
        indexes = @Index(name = "idx_user_permissions_user", columnList = "user_id")
)
@Getter
@Setter
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_permission_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "permission_name", nullable = false, length = 80)
    private String permissionName;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
