package cn.ccut.seat.web;

import cn.ccut.seat.domain.Reservation;
import cn.ccut.seat.service.CheckinService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/checkin")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    public record CheckinRequest(String token, String deviceId) {
    }

    @PostMapping
    public Map<String, Object> checkin(@RequestBody CheckinRequest req) {
        Reservation r = checkinService.checkin(req.token(), req.deviceId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reservationId", r.getId());
        data.put("seatId", r.getSeatId());
        data.put("status", r.getStatus().name());
        data.put("end", r.getEndTime().toString());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", "OK");
        m.put("message", "签到成功");
        m.put("data", data);
        return m;
    }
}
