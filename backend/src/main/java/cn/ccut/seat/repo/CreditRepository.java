package cn.ccut.seat.repo;

import cn.ccut.seat.domain.Credit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditRepository extends JpaRepository<Credit, Long> {
}
