package lk.customs.rms.repository;

import lk.customs.rms.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {
    List<UserPermission> findByUserId(Long userId);
    Optional<UserPermission> findByUserIdAndPermissionNameIgnoreCase(Long userId, String permissionName);
    void deleteByUserId(Long userId);
}
