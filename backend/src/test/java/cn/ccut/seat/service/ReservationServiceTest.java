package cn.ccut.seat.service;

import cn.ccut.seat.domain.Reservation;
import cn.ccut.seat.domain.ReservationStatus;
import cn.ccut.seat.domain.Seat;
import cn.ccut.seat.repo.CreditRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReservationService 单元测试：聚焦用户取消与正常结束的正常/异常分支。
 * 依赖全部 mock，不启动 Spring、不连数据库，秒级跑完。
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock SeatRepository seatRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock CreditRepository creditRepository;
    @Mock CodeSigner codeSigner;
    @Mock StringRedisTemplate redis;
    @Mock WaitlistService waitlistService;

    @InjectMocks
    ReservationService reservationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reservationService, "creditThreshold", 60);
        ReflectionTestUtils.setField(reservationService, "checkinCodeMinutes", 120);
    }

    private Reservation reservation(ReservationStatus status) {
        Reservation r = new Reservation();
        r.setId(5L);
        r.setUserId(1001L);
        r.setSeatId(21L);
        r.setStatus(status);
        return r;
    }

    @Test
    void cancelReservedSucceeds() {
        Reservation r = reservation(ReservationStatus.RESERVED);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));
        when(reservationRepository.markStatus(5L, ReservationStatus.RESERVED, ReservationStatus.CANCELLED))
                .thenReturn(1);
        when(seatRepository.findById(21L)).thenReturn(Optional.of(new Seat()));

        Reservation out = reservationService.cancel(5L, 1001L);

        assertEquals(ReservationStatus.CANCELLED, out.getStatus());
        verify(seatRepository).save(any(Seat.class));
        verify(waitlistService).notifyFirst(21L);
    }

    @Test
    void cancelByOtherUserIsRejected() {
        Reservation r = reservation(ReservationStatus.RESERVED);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));

        BizException ex = assertThrows(BizException.class, () -> reservationService.cancel(5L, 9999L));

        assertEquals("USER_MISMATCH", ex.getCode());
        verify(reservationRepository, never()).markStatus(anyLong(), any(), any());
        verify(waitlistService, never()).notifyFirst(anyLong());
    }

    @Test
    void cancelUsingStatusIsRejected() {
        Reservation r = reservation(ReservationStatus.USING);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));

        BizException ex = assertThrows(BizException.class, () -> reservationService.cancel(5L, 1001L));

        assertEquals("STATUS_INVALID", ex.getCode());
        verify(reservationRepository, never()).markStatus(anyLong(), any(), any());
    }

    @Test
    void finishUsingSucceeds() {
        Reservation r = reservation(ReservationStatus.USING);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));
        when(reservationRepository.markStatus(5L, ReservationStatus.USING, ReservationStatus.RELEASED))
                .thenReturn(1);
        when(seatRepository.findById(21L)).thenReturn(Optional.of(new Seat()));

        Reservation out = reservationService.finish(5L);

        assertEquals(ReservationStatus.RELEASED, out.getStatus());
        verify(waitlistService).notifyFirst(21L);
    }

    @Test
    void finishTerminalIsRejected() {
        Reservation r = reservation(ReservationStatus.RELEASED);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));

        BizException ex = assertThrows(BizException.class, () -> reservationService.finish(5L));

        assertEquals("STATUS_INVALID", ex.getCode());
        verify(reservationRepository, never()).markStatus(anyLong(), any(), any());
    }
}
