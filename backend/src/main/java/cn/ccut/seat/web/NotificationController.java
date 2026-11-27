package cn.ccut.seat.web;

import cn.ccut.seat.domain.Notification;
import cn.ccut.seat.repo.NotificationRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam Long userId) {
        List<Map<String, Object>> data = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::view).toList();
        return ok(data);
    }

    /** 未读通知数量，供前端显示小红点；data 直接返回数字 */
    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(@RequestParam Long userId) {
        return ok(notificationRepository.countByUserIdAndReadFalse(userId));
    }

    /** 标记单条通知为已读 */
    @PostMapping("/{id}/read")
    @Transactional
    public Map<String, Object> markRead(@PathVariable Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new BizException("NOTIFICATION_NOT_FOUND", "通知不存在"));
        n.setRead(true);
        notificationRepository.save(n);
        return ok(view(n));
    }

    /** 把某用户全部未读通知一键置为已读 */
    @PostMapping("/read-all")
    @Transactional
    public Map<String, Object> readAll(@RequestParam Long userId) {
        int updated = notificationRepository.markAllRead(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("updated", updated);
        return ok(data);
    }

    private Map<String, Object> view(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("type", n.getType());
        m.put("title", n.getTitle());
        m.put("content", n.getContent());
        m.put("seatId", n.getSeatId());
        m.put("read", n.getRead());
        m.put("createdAt", n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", "OK");
        m.put("message", "success");
        m.put("data", data);
        return m;
    }
}
