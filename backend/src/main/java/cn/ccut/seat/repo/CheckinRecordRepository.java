package cn.ccut.seat.repo;

import cn.ccut.seat.domain.CheckinRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinRecordRepository extends JpaRepository<CheckinRecord, Long> {
}
