package cn.ccut.seat.service;

import cn.ccut.seat.domain.Credit;
import cn.ccut.seat.domain.Notification;
import cn.ccut.seat.domain.WaitEntry;
import cn.ccut.seat.repo.CreditRepository;
import cn.ccut.seat.repo.NotificationRepository;
import cn.ccut.seat.repo.WaitEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 候补队列：座位释放后通知队列首位。
 * 通知同时落库（notification）并保留日志，后续接入公众号模板消息时
 * 只需在落库之外追加真实推送通道。
 */
@Service
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitEntryRepository waitEntryRepository;
    private final NotificationRepository notificationRepository;
    private final CreditRepository creditRepository;

    public WaitlistService(WaitEntryRepository waitEntryRepository,
                           NotificationRepository notificationRepository,
                           CreditRepository creditRepository) {
        this.waitEntryRepository = waitEntryRepository;
        this.notificationRepository = notificationRepository;
        this.creditRepository = creditRepository;
    }

    @Transactional
    public WaitEntry join(Long seatId, Long userId, LocalDateTime start, LocalDateTime end) {
        // 外键要求候补用户在信用表中存在，缺记录则初始化一条
        if (creditRepository.findById(userId).isEmpty()) {
            Credit c = new Credit();
            c.setUserId(userId);
            creditRepository.save(c);
        }
        WaitEntry e = new WaitEntry();
        e.setSeatId(seatId);
        e.setUserId(userId);
        e.setStartTime(start);
        e.setEndTime(end);
        return waitEntryRepository.save(e);
    }

    @Transactional
    public void notifyFirst(Long seatId) {
        waitEntryRepository.findFirstBySeatIdAndNotifiedFalseOrderByCreatedAtAsc(seatId)
                .ifPresent(e -> {
                    e.setNotified(true);
                    waitEntryRepository.save(e);

                    Notification n = new Notification();
                    n.setUserId(e.getUserId());
                    n.setType("WAIT_NOTIFY");
                    n.setTitle("候补叫号提醒");
                    n.setContent("您候补的座位已空闲，请尽快前往预约");
                    n.setSeatId(seatId);
                    notificationRepository.save(n);

                    log.info("waitlist notified: seat={} user={}", seatId, e.getUserId());
                });
    }
}
