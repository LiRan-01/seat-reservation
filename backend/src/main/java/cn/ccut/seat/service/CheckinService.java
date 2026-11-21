package cn.ccut.seat.service;

import cn.ccut.seat.domain.*;
import cn.ccut.seat.repo.CheckinRecordRepository;
import cn.ccut.seat.repo.ReservationRepository;
import cn.ccut.seat.repo.SeatRepository;
import cn.ccut.seat.security.CodeSigner;
import cn.ccut.seat.web.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class CheckinService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final CheckinRecordRepository checkinRecordRepository;
    private final CodeSigner codeSigner;
    private final StringRedisTemplate redis;

    @Value("${app.checkin-window-minutes}")
    private int windowMinutes;

    public CheckinService(ReservationRepository reservationRepository,
                          SeatRepository seatRepository,
                          CheckinRecordRepository checkinRecordRepository,
                          CodeSigner codeSigner,
                          StringRedisTemplate redis) {
        this.reservationRepository = reservationRepository;
        this.seatRepository = seatRepository;
        this.checkinRecordRepository = checkinRecordRepository;
        this.codeSigner = codeSigner;
        this.redis = redis;
    }

    /** 签到：验签 -> 校验时间窗 -> 一次性随机数防重放 -> 状态迁移 */
    @Transactional
    public Reservation checkin(String token, String deviceId) {
        CodeSigner.Payload payload = codeSigner.verify(token);

        Boolean fresh = redis.opsForValue()
                .setIfAbsent("nonce:" + payload.nonce(), "1", Duration.ofMinutes(windowMinutes + 5));
        if (Boolean.FALSE.equals(fresh)) {
            throw new BizException("REPLAY", "签到码已被使用");
        }

        Reservation r = reservationRepository.findById(payload.reservationId())
                .orElseThrow(() -> new BizException("RESERVATION_NOT_FOUND", "预约单不存在"));
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(r.getStartTime().minusMinutes(windowMinutes))
                || now.isAfter(r.getStartTime().plusMinutes(windowMinutes))) {
            throw new BizException("OUT_OF_WINDOW", "不在签到时间窗内");
        }
        int rows = reservationRepository.markStatus(r.getId(), ReservationStatus.RESERVED, ReservationStatus.USING);
        if (rows == 0) {
            throw new BizException("STATUS_INVALID", "预约单当前状态不可签到");
        }
        r.setStatus(ReservationStatus.USING);
        seatRepository.findById(r.getSeatId()).ifPresent(s -> {
            s.setStatus(SeatStatus.USING);
            seatRepository.save(s);
        });

        CheckinRecord rec = new CheckinRecord();
        rec.setReservationId(r.getId());
        rec.setType("CHECK_IN");
        rec.setDeviceId(deviceId);
        checkinRecordRepository.save(rec);
        return r;
    }
}
