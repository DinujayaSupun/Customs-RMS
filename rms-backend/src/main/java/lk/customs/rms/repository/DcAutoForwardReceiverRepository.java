package lk.customs.rms.repository;

import lk.customs.rms.entity.DcAutoForwardReceiver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DcAutoForwardReceiverRepository extends JpaRepository<DcAutoForwardReceiver, Long> {
    List<DcAutoForwardReceiver> findAllByOrderByDcUserIdAsc();
    Optional<DcAutoForwardReceiver> findByDcUserId(Long dcUserId);
    void deleteByDcUserId(Long dcUserId);

    default Map<Long, Long> receiverIdByDcUserId() {
        return findAllByOrderByDcUserIdAsc().stream()
                .collect(java.util.stream.Collectors.toMap(
                        DcAutoForwardReceiver::getDcUserId,
                        DcAutoForwardReceiver::getReceiverUserId));
    }
}
