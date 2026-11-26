package cn.ccut.seat.service;

import cn.ccut.seat.domain.*;
import cn.ccut.seat.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 占座检测定时任务：每分钟扫描一次未签到与暂离超时的预约。
 * 只做条件更新，影响行数为 1 才继续，重复执行不会重复扣分。
 */
@Service
public class ReleaseJob {

    private static final Logger log = LoggerFactory.getLogger(ReleaseJob.class);

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final ViolationRepository violationRepository;
    private final WaitlistService waitlistService;

    @Value("${app.no-show-minutes}")
    private int noShowMinutes;

    @Value("${app.violation-score}")
    private int violationScore;

    public ReleaseJob(ReservationRepository reservationRepository,
                      SeatRepository seatRepository,
                      ViolationRepository violationRepository,
                      WaitlistService waitlistService) {
        this.reservationRepository = reservationRepository;
        this.seatRepository = seatRepository;
        this.violationRepository = violationRepository;
        this.waitlistService = waitlistService;
    }

    @Scheduled(fixedDelay = 60000L, initialDelay = 60000L)
    @Transactional
    public void scan() {
        long t0 = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        int released = 0;

        List<Reservation> noShows =
                reservationRepository.findByStatusAndStartTimeBefore(ReservationStatus.RESERVED,
                        now.minusMinutes(noShowMinutes));
        for (Reservation r : noShows) {
            released += release(r, "NO_SHOW");
        }

        List<Reservation> awayTimeouts =
                reservationRepository.findByStatusAndAwayUntilBefore(ReservationStatus.AWAY, now);
        for (Reservation r : awayTimeouts) {
            released += release(r, "AWAY_TIMEOUT");
        }

        if (released > 0) {
            log.info("release job finished, released={}, cost={}ms",
                    released, System.currentTimeMillis() - t0);
        }
    }

    private int release(Reservation r, String type) {
        int rows = reservationRepository.markStatus(r.getId(), r.getStatus(), ReservationStatus.NO_SHOW);
        if (rows == 0) {
            return 0;
        }
        seatRepository.findById(r.getSeatId()).ifPresent(s -> {
            s.setStatus(SeatStatus.AVAILABLE);
            seatRepository.save(s);
        });
        Violation v = new Violation();
        v.setReservationId(r.getId());
        v.setUserId(r.getUserId());
        v.setType(type);
        v.setScore(violationScore);
        violationRepository.save(v);
        waitlistService.notifyFirst(r.getSeatId());
        return 1;
    }
}
