package cn.ccut.seat.web;

import cn.ccut.seat.domain.Reservation;
import cn.ccut.seat.service.ReservationService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    public record ReserveRequest(Long userId, Long seatId, String start, String end) {
    }

    @PostMapping
    public Map<String, Object> reserve(@RequestBody ReserveRequest req) {
        Reservation r = reservationService.reserve(req.userId(), req.seatId(),
                LocalDateTime.parse(req.start()), LocalDateTime.parse(req.end()));
        return ok(view(r));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return ok(view(reservationService.get(id)));
    }

    @PostMapping("/{id}/away")
    public Map<String, Object> away(@PathVariable Long id,
                                    @RequestParam(defaultValue = "30") int minutes) {
        return ok(view(reservationService.away(id, minutes)));
    }

    @PostMapping("/{id}/back")
    public Map<String, Object> back(@PathVariable Long id) {
        return ok(view(reservationService.back(id)));
    }

    @PostMapping("/{id}/release")
    public Map<String, Object> release(@PathVariable Long id,
                                       @RequestParam(defaultValue = "现场核实") String reason) {
        return ok(view(reservationService.forceRelease(id, reason)));
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam Long userId) {
        return ok(reservationService.listByUser(userId).stream().map(this::view).toList());
    }

    private Map<String, Object> view(Reservation r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("seatId", r.getSeatId());
        m.put("status", r.getStatus().name());
        m.put("start", r.getStartTime() == null ? null : r.getStartTime().toString());
        m.put("end", r.getEndTime() == null ? null : r.getEndTime().toString());
        m.put("checkinCode", r.getCheckinCode());
        m.put("awayUntil", r.getAwayUntil() == null ? null : r.getAwayUntil().toString());
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
