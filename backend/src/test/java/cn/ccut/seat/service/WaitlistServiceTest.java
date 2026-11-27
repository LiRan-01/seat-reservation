package cn.ccut.seat.service;

import cn.ccut.seat.domain.Notification;
import cn.ccut.seat.domain.WaitEntry;
import cn.ccut.seat.repo.CreditRepository;
import cn.ccut.seat.repo.NotificationRepository;
import cn.ccut.seat.repo.WaitEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WaitlistService 单元测试：候补登记、叫号落库与排队位置估算。
 */
@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock WaitEntryRepository waitEntryRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock CreditRepository creditRepository;

    @InjectMocks
    WaitlistService waitlistService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(waitlistService, "waitEstimateMinutes", 30L);
    }

    @Test
    void joinInitializesCreditWhenMissing() {
        LocalDateTime now = LocalDateTime.now();
        when(creditRepository.findById(1001L)).thenReturn(Optional.empty());
        when(waitEntryRepository.save(any(WaitEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        waitlistService.join(21L, 1001L, now, now.plusHours(2));

        verify(creditRepository).save(any());
        verify(waitEntryRepository).save(any(WaitEntry.class));
    }

    @Test
    void notifyFirstWritesNotificationWhenWaiterExists() {
        WaitEntry e = new WaitEntry();
        e.setUserId(1001L);
        e.setSeatId(21L);
        when(waitEntryRepository.findFirstBySeatIdAndNotifiedFalseOrderByIdAsc(21L))
                .thenReturn(Optional.of(e));

        waitlistService.notifyFirst(21L);

        verify(waitEntryRepository).save(any(WaitEntry.class));
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyFirstDoesNothingWhenQueueEmpty() {
        when(waitEntryRepository.findFirstBySeatIdAndNotifiedFalseOrderByIdAsc(21L))
                .thenReturn(Optional.empty());

        waitlistService.notifyFirst(21L);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void describeCalculatesAheadAndWaitMinutes() {
        WaitEntry e = new WaitEntry();
        e.setSeatId(21L);
        ReflectionTestUtils.setField(e, "id", 5L);
        when(waitEntryRepository.countBySeatIdAndNotifiedFalseAndIdLessThan(21L, 5L)).thenReturn(2L);

        WaitlistService.WaitPosition p = waitlistService.describe(e);

        assertEquals(2L, p.aheadCount());
        assertEquals(60L, p.estimatedWaitMinutes());
    }
}
