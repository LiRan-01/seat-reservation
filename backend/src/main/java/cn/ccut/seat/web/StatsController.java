package cn.ccut.seat.web;

import cn.ccut.seat.domain.Reservation;
import cn.ccut.seat.domain.ReservationStatus;
import cn.ccut.seat.repo.ReservationRepository;
import cn.ccut.seat.repo.SeatRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 统计报表：按自然日统计上座率。
 * 上座率 = 有效预约占用时长 / (座位数 × 开放时长)。开放时间按 08:00-22:00 计。
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private static final int OPEN_HOUR = 8;
    private static final int CLOSE_HOUR = 22;

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;

    public StatsController(ReservationRepository reservationRepository,
                          SeatRepository seatRepository) {
        this.reservationRepository = reservationRepository;
        this.seatRepository = seatRepository;
    }

    @GetMapping("/occupancy")
    public Map<String, Object> occupancy(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        long seatCount = seatRepository.count();
        double availableHours = seatCount * (CLOSE_HOUR - OPEN_HOUR);

        List<Map<String, Object>> days = new ArrayList<>();
        LocalDate day = from;
        while (!day.isAfter(to)) {
            LocalDateTime dayStart = day.atTime(OPEN_HOUR, 0);
            LocalDateTime dayEnd = day.atTime(CLOSE_HOUR, 0);
            List<Reservation> list = reservationRepository.findByStartTimeBetween(dayStart, dayEnd);

            long valid = 0;
            double seatHours = 0;
            for (Reservation r : list) {
                if (r.getStatus() == ReservationStatus.RELEASED
                        || r.getStatus() == ReservationStatus.FORCED
                        || r.getStatus() == ReservationStatus.NO_SHOW) {
                    continue;
                }
                valid++;
                LocalDateTime s = r.getStartTime().isBefore(dayStart) ? dayStart : r.getStartTime();
                LocalDateTime e = r.getEndTime().isAfter(dayEnd) ? dayEnd : r.getEndTime();
                if (e.isAfter(s)) {
                    seatHours += Duration.between(s, e).toMinutes() / 60.0;
                }
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day.toString());
            m.put("reservations", valid);
            m.put("seatHours", Math.round(seatHours * 10) / 10.0);
            m.put("availableHours", availableHours);
            m.put("rate", availableHours == 0 ? 0.0
                    : Math.round(seatHours / availableHours * 1000) / 1000.0);
            days.add(m);
            day = day.plusDays(1);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", "OK");
        m.put("message", "success");
        m.put("data", days);
        return m;
    }
}
