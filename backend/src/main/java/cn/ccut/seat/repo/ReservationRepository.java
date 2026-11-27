package cn.ccut.seat.repo;

import cn.ccut.seat.domain.Reservation;
import cn.ccut.seat.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** 目标时段是否已被有效预约占用 */
    @Query("""
           select count(r) from Reservation r
           where r.seatId = :seatId
             and r.status in (cn.ccut.seat.domain.ReservationStatus.RESERVED,
                              cn.ccut.seat.domain.ReservationStatus.USING,
                              cn.ccut.seat.domain.ReservationStatus.AWAY)
             and r.startTime < :end and r.endTime > :start
           """)
    long countOverlap(@Param("seatId") Long seatId,
                      @Param("start") LocalDateTime start,
                      @Param("end") LocalDateTime end);

    /**
     * 批量查询某时段内所有重叠的有效预约（座位图接口用）。
     * 一次取全量再在内存里按座位匹配，避免对每个座位各查一次形成 N+1。
     * 过滤条件与 countOverlap 保持一致。
     */
    @Query("""
           select r from Reservation r
           where r.status in (cn.ccut.seat.domain.ReservationStatus.RESERVED,
                              cn.ccut.seat.domain.ReservationStatus.USING,
                              cn.ccut.seat.domain.ReservationStatus.AWAY)
             and r.startTime < :end and r.endTime > :start
           """)
    List<Reservation> findOverlapping(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /** 条件更新：只有当前状态匹配才生效，保证定时任务幂等 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Reservation r set r.status = :to where r.id = :id and r.status = :from")
    int markStatus(@Param("id") Long id,
                   @Param("from") ReservationStatus from,
                   @Param("to") ReservationStatus to);

    List<Reservation> findByStatusAndStartTimeBefore(ReservationStatus status, LocalDateTime time);

    List<Reservation> findByStatusAndAwayUntilBefore(ReservationStatus status, LocalDateTime time);

    List<Reservation> findBySeatIdAndStatusIn(Long seatId, List<ReservationStatus> statuses);

    /** 统计报表：取某一天内的预约（按开始时间落在当天） */
    List<Reservation> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);

    /** 按学号查询本人全部预约，由数据库直接过滤，避免全表加载后内存筛选 */
    List<Reservation> findByUserId(Long userId);
}
