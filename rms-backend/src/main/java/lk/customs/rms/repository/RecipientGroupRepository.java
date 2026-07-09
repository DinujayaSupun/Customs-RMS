package lk.customs.rms.repository;

import lk.customs.rms.entity.RecipientGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecipientGroupRepository extends JpaRepository<RecipientGroup, Long> {
    List<RecipientGroup> findAllByOrderByNameAsc();
    Optional<RecipientGroup> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
