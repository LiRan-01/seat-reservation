package cn.ccut.seat.service;

import cn.ccut.seat.domain.Credit;
import cn.ccut.seat.domain.Notification;
import cn.ccut.seat.domain.WaitEntry;
import cn.ccut.seat.repo.CreditRepository;
import cn.ccut.seat.repo.NotificationRepository;
import cn.ccut.seat.repo.WaitEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    /** 排队位置与预计等待时间，estimatedWaitMinutes = 前面人数 × 单人估算分钟 */
    public record WaitPosition(Long id, Long seatId, long aheadCount, long estimatedWaitMinutes) {
    }

    @Value("${app.wait-estimate-minutes}")
    private long waitEstimateMinutes;

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

    /** 计算某条候补前面有几人、预计还要等多久；用自增 id 判断先后，避免同秒时间戳相等 */
    @Transactional(readOnly = true)
    public WaitPosition describe(WaitEntry e) {
        long ahead = waitEntryRepository
                .countBySeatIdAndNotifiedFalseAndIdLessThan(e.getSeatId(), e.getId());
        return new WaitPosition(e.getId(), e.getSeatId(), ahead, ahead * waitEstimateMinutes);
    }

    /** 某用户当前全部在排队候补的位置信息 */
    @Transactional(readOnly = true)
    public List<WaitPosition> listPositions(Long userId) {
        return waitEntryRepository.findByUserIdAndNotifiedFalseOrderByIdAsc(userId)
                .stream().map(this::describe).toList();
    }

    @Transactional
    public void notifyFirst(Long seatId) {
        waitEntryRepository.findFirstBySeatIdAndNotifiedFalseOrderByIdAsc(seatId)
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
