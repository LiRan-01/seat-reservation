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
    private final WaitlistService waitlistService;

    @Value("${app.credit-threshold}")
    private int creditThreshold;

    @Value("${app.checkin-code-minutes}")
    private int checkinCodeMinutes;

    public ReservationService(SeatRepository seatRepository,
                              ReservationRepository reservationRepository,
                              CreditRepository creditRepository,
                              CodeSigner codeSigner,
                              StringRedisTemplate redis,
                              WaitlistService waitlistService) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.creditRepository = creditRepository;
        this.codeSigner = codeSigner;
        this.redis = redis;
        this.waitlistService = waitlistService;
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
        // 信用记录不存在则先初始化（默认 100 分），同时保证外键可写入
        Credit credit = creditRepository.findById(userId).orElseGet(() -> {
            Credit c = new Credit();
            c.setUserId(userId);
            return creditRepository.save(c);
        });
        if (credit.getScore() < creditThreshold) {
            throw new BizException("CREDIT_LOW", "信用分不足，暂不能预约");
        }

        Reservation r = new Reservation();
        r.setUserId(userId);
        r.setSeatId(seatId);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setStatus(ReservationStatus.RESERVED);
        r = reservationRepository.saveAndFlush(r);
        r.setCheckinCode(codeSigner.sign(r.getId(), seatId, checkinCodeMinutes));
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
        if (isTerminal(r.getStatus())) {
            throw new BizException("STATUS_INVALID", "该预约已是终态");
        }
        int rows = reservationRepository.markStatus(reservationId, r.getStatus(), ReservationStatus.FORCED);
        if (rows == 0) {
            throw new BizException("STATUS_INVALID", "状态已变化，请刷新后重试");
        }
        r.setStatus(ReservationStatus.FORCED);
        updateSeat(r.getSeatId(), SeatStatus.AVAILABLE);
        waitlistService.notifyFirst(r.getSeatId());
        return r;
    }

    /** 用户主动取消：仅未签到的预约可取消，不扣分，释放座位并通知候补 */
    @Transactional
    public Reservation cancel(Long reservationId, Long userId) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BizException("RESERVATION_NOT_FOUND", "预约单不存在"));
        if (userId != null && !r.getUserId().equals(userId)) {
            throw new BizException("USER_MISMATCH", "只能取消本人的预约");
        }
        if (r.getStatus() != ReservationStatus.RESERVED) {
            throw new BizException("STATUS_INVALID", "只有未签到的预约可以取消");
        }
        int rows = reservationRepository.markStatus(reservationId,
                ReservationStatus.RESERVED, ReservationStatus.CANCELLED);
        if (rows == 0) {
            throw new BizException("STATUS_INVALID", "状态已变化，请刷新后重试");
        }
        r.setStatus(ReservationStatus.CANCELLED);
        updateSeat(r.getSeatId(), SeatStatus.AVAILABLE);
        waitlistService.notifyFirst(r.getSeatId());
        return r;
    }

    /** 用户正常结束使用：非终态预约统一置为 RELEASED，让该状态真正生效 */
    @Transactional
    public Reservation finish(Long reservationId) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BizException("RESERVATION_NOT_FOUND", "预约单不存在"));
        ReservationStatus from = r.getStatus();
        if (isTerminal(from)) {
            throw new BizException("STATUS_INVALID", "该预约已是终态");
        }
        int rows = reservationRepository.markStatus(reservationId, from, ReservationStatus.RELEASED);
        if (rows == 0) {
            throw new BizException("STATUS_INVALID", "状态已变化，请刷新后重试");
        }
        r.setStatus(ReservationStatus.RELEASED);
        updateSeat(r.getSeatId(), SeatStatus.AVAILABLE);
        waitlistService.notifyFirst(r.getSeatId());
        return r;
    }

    private boolean isTerminal(ReservationStatus status) {
        return status == ReservationStatus.RELEASED
                || status == ReservationStatus.NO_SHOW
                || status == ReservationStatus.FORCED
                || status == ReservationStatus.CANCELLED;
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
