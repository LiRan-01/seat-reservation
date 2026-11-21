package cn.ccut.seat.service;

import cn.ccut.seat.domain.*;
import cn.ccut.seat.repo.*;
import cn.ccut.seat.security.CodeSigner;
import cn.ccut.seat.web.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationService {

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final CreditRepository creditRepository;
    private final CodeSigner codeSigner;
    private final StringRedisTemplate redis;

    @Value("${app.credit-threshold}")
    private int creditThreshold;

    public ReservationService(SeatRepository seatRepository,
                              ReservationRepository reservationRepository,
                              CreditRepository creditRepository,
                              CodeSigner codeSigner,
                              StringRedisTemplate redis) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.creditRepository = creditRepository;
        this.codeSigner = codeSigner;
        this.redis = redis;
    }

    /** 预约：行锁 + 时段冲突校验 + 信用门槛，三步在同一事务内完成 */
    @Transactional
    public Reservation reserve(Long userId, Long seatId, LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || start.isAfter(end) || start.isEqual(end)) {
            throw new BizException("TIME_INVALID", "预约时段不合法");
        }
        Seat seat = seatRepository.findWithLock(seatId)
                .orElseThrow(() -> new BizException("SEAT_NOT_FOUND", "座位不存在"));
        if (seat.getStatus() == SeatStatus.DISABLED) {
            throw new BizException("SEAT_DISABLED", "座位已停用");
        }
        if (reservationRepository.countOverlap(seatId, start, end) > 0) {
            throw new BizException("TIME_CONFLICT", "该时段座位已被预约");
        }
        int score = creditRepository.findById(userId).map(Credit::getScore).orElse(100);
        if (score < creditThreshold) {
            throw new BizException("CREDIT_LOW", "信用分不足，暂不能预约");
        }

        Reservation r = new Reservation();
        r.setUserId(userId);
        r.setSeatId(seatId);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setStatus(ReservationStatus.RESERVED);
        r = reservationRepository.saveAndFlush(r);
        r.setCheckinCode(codeSigner.sign(r.getId(), seatId, 120));
        reservationRepository.save(r);

        seat.setStatus(SeatStatus.RESERVED);
        seatRepository.save(seat);
        redis.opsForValue().set("seat:hold:" + seatId + ":" + start,
                String.valueOf(userId), Duration.ofMinutes(5));
        return r;
    }

    @Transactional
    public Reservation away(Long reservationId, int minutes) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BizException("RESERVATION_NOT_FOUND", "预约单不存在"));
        if (r.getStatus() != ReservationStatus.USING) {
            throw new BizException("STATUS_INVALID", "只有使用中的预约可以暂离");
        }
        r.setStatus(ReservationStatus.AWAY);
        r.setAwayUntil(LocalDateTime.now().plusMinutes(Math.min(minutes, 30)));
        reservationRepository.save(r);
        updateSeat(r.getSeatId(), SeatStatus.AWAY);
        return r;
    }

    @Transactional
    public Reservation back(Long reservationId) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BizException("RESERVATION_NOT_FOUND", "预约单不存在"));
        if (r.getStatus() != ReservationStatus.AWAY) {
            throw new BizException("STATUS_INVALID", "当前不是暂离状态");
        }
        r.setStatus(ReservationStatus.USING);
        r.setAwayUntil(null);
        reservationRepository.save(r);
        updateSeat(r.getSeatId(), SeatStatus.USING);
        return r;
    }

    @Transactional
    public Reservation forceRelease(Long reservationId, String reason) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BizException("RESERVATION_NOT_FOUND", "预约单不存在"));
        if (r.getStatus() == ReservationStatus.RELEASED
                || r.getStatus() == ReservationStatus.NO_SHOW
                || r.getStatus() == ReservationStatus.FORCED) {
            throw new BizException("STATUS_INVALID", "该预约已是终态");
        }
        int rows = reservationRepository.markStatus(reservationId, r.getStatus(), ReservationStatus.FORCED);
        if (rows == 0) {
            throw new BizException("STATUS_INVALID", "状态已变化，请刷新后重试");
        }
        updateSeat(r.getSeatId(), SeatStatus.AVAILABLE);
        return r;
    }

    public Reservation get(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new BizException("RESERVATION_NOT_FOUND", "预约单不存在"));
    }

    public List<Reservation> listByUser(Long userId) {
        return reservationRepository.findAll().stream()
                .filter(r -> r.getUserId().equals(userId))
                .toList();
    }

    public void updateSeat(Long seatId, SeatStatus status) {
        seatRepository.findById(seatId).ifPresent(s -> {
            s.setStatus(status);
            seatRepository.save(s);
        });
    }
}
