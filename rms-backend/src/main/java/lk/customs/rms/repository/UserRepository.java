package lk.customs.rms.repository;

import lk.customs.rms.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByUsernameAndIsActiveTrue(String username);
	List<User> findByIsActiveTrueOrderByFullNameAsc();
	List<User> findByIsActiveTrueAndRole_RoleNameOrderByFullNameAsc(String roleName);
	List<User> findByIsActiveTrueAndRole_RoleNameNotOrderByFullNameAsc(String roleName);
	Optional<User> findByUsernameIgnoreCase(String username);
	boolean existsByUsernameIgnoreCase(String username);
	long countByRole_RoleNameAndIsActiveTrue(String roleName);

	@Query("""
		select u from User u
		where (:search is null or :search = ''
			or lower(u.fullName) like lower(concat('%', :search, '%'))
			or lower(u.username) like lower(concat('%', :search, '%'))
			or lower(coalesce(u.email, '')) like lower(concat('%', :search, '%'))
			or lower(coalesce(u.phone, '')) like lower(concat('%', :search, '%'))
			or lower(coalesce(u.department, '')) like lower(concat('%', :search, '%')))
		  and (:role is null or :role = '' or upper(u.role.roleName) = upper(:role))
		  and (:active is null or u.isActive = :active)
		""")
	Page<User> searchUsers(@Param("search") String search,
						   @Param("role") String role,
						   @Param("active") Boolean active,
						   Pageable pageable);
}
