package cn.ccut.seat.web;

import cn.ccut.seat.service.WaitlistService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    public record WaitRequest(Long seatId, Long userId, String start, String end) {
    }

    @PostMapping
    public Map<String, Object> join(@RequestBody WaitRequest req) {
        var entry = waitlistService.join(req.seatId(), req.userId(),
                LocalDateTime.parse(req.start()), LocalDateTime.parse(req.end()));
        WaitlistService.WaitPosition p = waitlistService.describe(entry);
        return ok(positionView(p), "已加入候补");
    }

    /** 查询某用户当前所有候补的排队位置与预计等待时间 */
    @GetMapping("/position")
    public Map<String, Object> position(@RequestParam Long userId) {
        List<Map<String, Object>> data = waitlistService.listPositions(userId)
                .stream().map(this::positionView).toList();
        return ok(data, "success");
    }

    private Map<String, Object> positionView(WaitlistService.WaitPosition p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("seatId", p.seatId());
        m.put("aheadCount", p.aheadCount());
        m.put("estimatedWaitMinutes", p.estimatedWaitMinutes());
        return m;
    }

    private Map<String, Object> ok(Object data, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", "OK");
        m.put("message", message);
        m.put("data", data);
        return m;
    }
}
