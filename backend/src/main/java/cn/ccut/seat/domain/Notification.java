package cn.ccut.seat.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 站内通知：候补叫号等事件落库，用户可在前端查询，
 * 不再仅依赖后台日志。
 */
@Entity
@Table(name = "notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    /** WAIT_NOTIFY 候补叫号 */
    private String type;

    private String title;

    @Column(length = 512)
    private String content;

    @Column(name = "seat_id")
    private Long seatId;

    @Column(name = "is_read")
    private Boolean read = Boolean.FALSE;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getSeatId() { return seatId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }
    public Boolean getRead() { return read; }
    public void setRead(Boolean read) { this.read = read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
