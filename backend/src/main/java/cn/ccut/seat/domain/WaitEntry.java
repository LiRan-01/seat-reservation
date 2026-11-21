package cn.ccut.seat.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "wait_entry")
public class WaitEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_id")
    private Long seatId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    private Boolean notified = Boolean.FALSE;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getSeatId() { return seatId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Boolean getNotified() { return notified; }
    public void setNotified(Boolean notified) { this.notified = notified; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
