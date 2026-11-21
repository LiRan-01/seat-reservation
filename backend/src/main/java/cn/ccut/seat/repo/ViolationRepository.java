package cn.ccut.seat.repo;

import cn.ccut.seat.domain.Violation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViolationRepository extends JpaRepository<Violation, Long> {
}
