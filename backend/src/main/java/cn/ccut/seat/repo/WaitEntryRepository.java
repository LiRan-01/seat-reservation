package cn.ccut.seat.repo;

import cn.ccut.seat.domain.WaitEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaitEntryRepository extends JpaRepository<WaitEntry, Long> {

    /** 某座位仍在排队（未通知）的第一条候补，按自增 id 正序即插入先后 */
    Optional<WaitEntry> findFirstBySeatIdAndNotifiedFalseOrderByIdAsc(Long seatId);

    /** 某座位、仍在排队且 id 更小（更早登记）的候补条数，即前面有几人 */
    long countBySeatIdAndNotifiedFalseAndIdLessThan(Long seatId, Long id);

    /** 某用户当前仍在排队的全部候补，按登记先后（id 正序）排列，用于查询排队位置 */
    List<WaitEntry> findByUserIdAndNotifiedFalseOrderByIdAsc(Long userId);
}
