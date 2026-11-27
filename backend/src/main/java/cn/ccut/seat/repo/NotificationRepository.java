package cn.ccut.seat.repo;

import cn.ccut.seat.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 某用户的全部通知，按时间倒序 */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 某用户的未读通知数 */
    long countByUserIdAndReadFalse(Long userId);

    /** 某用户全部未读通知一键置为已读，返回受影响条数 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId);
}
