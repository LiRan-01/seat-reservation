package cn.ccut.seat.service;

import cn.ccut.seat.domain.WaitEntry;
import cn.ccut.seat.repo.WaitEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 候补队列：座位释放后通知队列首位。课程演示版以日志代替推送通道，
 * 接入公众号模板消息时替换 notify 实现即可。
 */
@Service
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitEntryRepository waitEntryRepository;

    public WaitlistService(WaitEntryRepository waitEntryRepository) {
        this.waitEntryRepository = waitEntryRepository;
    }

    public WaitEntry join(Long seatId, Long userId, LocalDateTime start, LocalDateTime end) {
        WaitEntry e = new WaitEntry();
        e.setSeatId(seatId);
        e.setUserId(userId);
        e.setStartTime(start);
        e.setEndTime(end);
        return waitEntryRepository.save(e);
    }

    public void notifyFirst(Long seatId) {
        waitEntryRepository.findFirstBySeatIdAndNotifiedFalseOrderByCreatedAtAsc(seatId)
                .ifPresent(e -> {
                    e.setNotified(true);
                    waitEntryRepository.save(e);
                    log.info("waitlist notified: seat={} user={}", seatId, e.getUserId());
                });
    }
}
