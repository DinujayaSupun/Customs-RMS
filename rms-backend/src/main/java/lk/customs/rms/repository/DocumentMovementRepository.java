package lk.customs.rms.repository;

import lk.customs.rms.entity.DocumentMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentMovementRepository extends JpaRepository<DocumentMovement, Long> {
    List<DocumentMovement> findByDocumentIdOrderByActionAtAsc(Long documentId);
}
