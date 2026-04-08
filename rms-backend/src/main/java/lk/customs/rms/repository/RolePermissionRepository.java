package lk.customs.rms.repository;

import lk.customs.rms.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRole_RoleNameIgnoreCaseOrderByPermissionNameAsc(String roleName);
    List<RolePermission> findAllByOrderByRole_RoleNameAscPermissionNameAsc();
    Optional<RolePermission> findByRole_IdAndPermissionNameIgnoreCase(Long roleId, String permissionName);
}