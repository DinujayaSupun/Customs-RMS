package lk.customs.rms.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "role_permissions",
        uniqueConstraints = @UniqueConstraint(name = "uk_role_permissions_role_permission", columnNames = {"role_id", "permission_name"})
)
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_permission_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "permission_name", nullable = false, length = 80)
    private String permissionName;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setPermissionName(String permissionName) {
        this.permissionName = permissionName;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}