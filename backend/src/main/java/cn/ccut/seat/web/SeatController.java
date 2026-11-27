package cn.ccut.seat.web;

import cn.ccut.seat.domain.*;
import cn.ccut.seat.repo.ReservationRepository;
import cn.ccut.seat.repo.RoomRepository;
import cn.ccut.seat.repo.SeatRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/rooms")
public class SeatController {

    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;

    public SeatController(RoomRepository roomRepository,
                          SeatRepository seatRepository,
                          ReservationRepository reservationRepository) {
        this.roomRepository = roomRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
    }

    @GetMapping
    public Map<String, Object> rooms() {
        return ok(roomRepository.findAll());
    }

    /** 座位图：返回座位基础状态 + 指定时段是否被占用（批量查询，避免 N+1） */
    @GetMapping("/{roomId}/seats")
    public Map<String, Object> seats(@PathVariable Long roomId,
                                     @RequestParam(required = false) String start,
                                     @RequestParam(required = false) String end) {
        LocalDateTime s = start == null ? LocalDateTime.now() : LocalDateTime.parse(start);
        LocalDateTime e = end == null ? s.plusHours(2) : LocalDateTime.parse(end);
        // 一次查出该时段所有重叠的有效预约，提取被占用的座位号集合
        Set<Long> busySeatIds = new HashSet<>();
        for (Reservation r : reservationRepository.findOverlapping(s, e)) {
            busySeatIds.add(r.getSeatId());
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (Seat seat : seatRepository.findByRoomIdOrderByCodeAsc(roomId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", seat.getId());
            m.put("code", seat.getCode());
            m.put("feature", seat.getFeature());
            m.put("status", seat.getStatus().name());
            m.put("busy", busySeatIds.contains(seat.getId()));
            data.add(m);
        }
        return ok(data);
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", "OK");
        m.put("message", "success");
        m.put("data", data);
        return m;
    }
}
