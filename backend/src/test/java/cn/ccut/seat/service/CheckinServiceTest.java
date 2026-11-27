package cn.ccut.seat.service;

import cn.ccut.seat.domain.CheckinRecord;
import cn.ccut.seat.domain.Reservation;
import cn.ccut.seat.domain.ReservationStatus;
import cn.ccut.seat.domain.Seat;
import cn.ccut.seat.domain.SeatStatus;
import cn.ccut.seat.repo.CheckinRecordRepository;
import cn.ccut.seat.repo.ReservationRepository;
import cn.ccut.seat.repo.SeatRepository;
import cn.ccut.seat.security.CodeSigner;
import cn.ccut.seat.web.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CheckinService 单元测试：验签后的一次性随机数防重放、签到时间窗与状态原子迁移。
 */
@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    @Mock ReservationRepository reservationRepository;
    @Mock SeatRepository seatRepository;
    @Mock CheckinRecordRepository checkinRecordRepository;
    @Mock CodeSigner codeSigner;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks
    CheckinService checkinService;

    private static final CodeSigner.Payload PAYLOAD =
            new CodeSigner.Payload(1L, 21L, 9999999999999L, "nonce1");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(checkinService, "windowMinutes", 15);
    }

    private Reservation reservation(LocalDateTime start, ReservationStatus status) {
        Reservation r = new Reservation();
        ReflectionTestUtils.setField(r, "id", 1L);
        r.setUserId(1001L);
        r.setSeatId(21L);
        r.setStartTime(start);
        r.setEndTime(start.plusHours(2));
        r.setStatus(status);
        return r;
    }

    private void mockNonce(boolean fresh) {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("nonce:nonce1"), eq("1"), any(Duration.class)))
                .thenReturn(fresh);
    }

    @Test
    void checkinSuccessMovesToUsing() {
        LocalDateTime now = LocalDateTime.now();
        Reservation r = reservation(now, ReservationStatus.RESERVED);
        Seat seat = new Seat();
        when(codeSigner.verify("token")).thenReturn(PAYLOAD);
        mockNonce(true);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reservationRepository.markStatus(1L, ReservationStatus.RESERVED, ReservationStatus.USING))
                .thenReturn(1);
        when(seatRepository.findById(21L)).thenReturn(Optional.of(seat));

        Reservation result = checkinService.checkin("token", "device-1");

        assertEquals(ReservationStatus.USING, result.getStatus());
        assertEquals(SeatStatus.USING, seat.getStatus());
        verify(seatRepository).save(any(Seat.class));
        verify(checkinRecordRepository).save(any(CheckinRecord.class));
    }

    @Test
    void replayWhenNonceAlreadyUsed() {
        when(codeSigner.verify("token")).thenReturn(PAYLOAD);
        mockNonce(false);

        BizException ex = assertThrows(BizException.class,
                () -> checkinService.checkin("token", "device-1"));
        assertEquals("REPLAY", ex.getCode());
        verify(reservationRepository, never()).findById(any());
    }

    @Test
    void throwsWhenReservationNotFound() {
        when(codeSigner.verify("token")).thenReturn(PAYLOAD);
        mockNonce(true);
        when(reservationRepository.findById(1L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> checkinService.checkin("token", "device-1"));
        assertEquals("RESERVATION_NOT_FOUND", ex.getCode());
    }

    @Test
    void outOfWindowWhenStartTimeTooFar() {
        Reservation r = reservation(LocalDateTime.now().plusHours(3), ReservationStatus.RESERVED);
        when(codeSigner.verify("token")).thenReturn(PAYLOAD);
        mockNonce(true);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(r));

        BizException ex = assertThrows(BizException.class,
                () -> checkinService.checkin("token", "device-1"));
        assertEquals("OUT_OF_WINDOW", ex.getCode());
        verify(reservationRepository, never()).markStatus(any(), any(), any());
    }

    @Test
    void statusInvalidWhenMarkUpdatesZeroRow() {
        Reservation r = reservation(LocalDateTime.now(), ReservationStatus.USING);
        when(codeSigner.verify("token")).thenReturn(PAYLOAD);
        mockNonce(true);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reservationRepository.markStatus(1L, ReservationStatus.RESERVED, ReservationStatus.USING))
                .thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> checkinService.checkin("token", "device-1"));
        assertEquals("STATUS_INVALID", ex.getCode());
        verify(checkinRecordRepository, never()).save(any());
    }
}
