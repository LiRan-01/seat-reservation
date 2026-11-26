package cn.ccut.seat.repo;

import cn.ccut.seat.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 某用户的全部通知，按时间倒序 */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
}
