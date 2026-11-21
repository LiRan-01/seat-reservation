package cn.ccut.seat.repo;

import cn.ccut.seat.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
}
