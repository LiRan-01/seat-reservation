package cn.ccut.seat.web;

import cn.ccut.seat.service.WaitlistService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", "OK");
        m.put("message", "已加入候补");
        m.put("data", Map.of("id", waitlistService.join(req.seatId(), req.userId(),
                LocalDateTime.parse(req.start()), LocalDateTime.parse(req.end())).getId()));
        return m;
    }
}
