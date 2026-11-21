package cn.ccut.seat.repo;

import cn.ccut.seat.domain.WaitEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WaitEntryRepository extends JpaRepository<WaitEntry, Long> {

    Optional<WaitEntry> findFirstBySeatIdAndNotifiedFalseOrderByCreatedAtAsc(Long seatId);
}
