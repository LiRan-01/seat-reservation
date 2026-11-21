package cn.ccut.seat.repo;

import cn.ccut.seat.domain.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByRoomIdOrderByCodeAsc(Long roomId);

    /** 预约时对座位行加锁，把“检查—写入”变成原子操作 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findWithLock(@Param("id") Long id);
}
