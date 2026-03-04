package lk.customs.rms.repository;

import lk.customs.rms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByUsernameAndIsActiveTrue(String username);
	List<User> findByIsActiveTrueOrderByFullNameAsc();
}
