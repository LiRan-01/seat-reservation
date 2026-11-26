package cn.ccut.seat.web;

import cn.ccut.seat.domain.Notification;
import cn.ccut.seat.repo.NotificationRepository;
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
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", "OK");
        m.put("message", "success");
        m.put("data", data);
        return m;
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
}
