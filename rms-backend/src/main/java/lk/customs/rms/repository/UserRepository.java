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
	@Query("""
		select u from User u
		where lower(u.username) = lower(:username)
		  and u.isActive = true
		  and u.isDeleted = false
		""")
	Optional<User> findByUsernameIgnoreCaseAndIsActiveTrue(@Param("username") String username);

	@Query("""
		select u from User u
		where u.isActive = true
		  and u.isDeleted = false
		order by u.fullName asc
		""")
	List<User> findByIsActiveTrueOrderByFullNameAsc();

	@Query("""
		select u from User u
		where u.isActive = true
		  and u.isDeleted = false
		  and upper(u.role.roleName) = upper(:roleName)
		order by u.fullName asc
		""")
	List<User> findByIsActiveTrueAndRole_RoleNameOrderByFullNameAsc(@Param("roleName") String roleName);

	@Query("""
		select u from User u
		where u.isActive = true
		  and u.isDeleted = false
		  and upper(u.role.roleName) <> upper(:roleName)
		order by u.fullName asc
		""")
	List<User> findByIsActiveTrueAndRole_RoleNameNotOrderByFullNameAsc(@Param("roleName") String roleName);

	Optional<User> findByUsernameIgnoreCase(String username);

	@Query("""
		select count(u) > 0 from User u
		where lower(u.username) = lower(:username)
		""")
	boolean existsByUsernameIgnoreCase(@Param("username") String username);

	@Query("""
		select count(u) > 0 from User u
		where lower(u.username) = lower(:username)
		  and u.id <> :id
		""")
	boolean existsByUsernameIgnoreCaseAndIdNot(@Param("username") String username, @Param("id") Long id);
	@Query("""
		select count(u) from User u
		where upper(u.role.roleName) = upper(:roleName)
		  and u.isActive = true
		  and u.isDeleted = false
		""")
	long countActiveNotDeletedByRoleName(@Param("roleName") String roleName);

	@Query("""
		select u from User u
		where u.isDeleted = false
		  and (:search is null or :search = ''
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
