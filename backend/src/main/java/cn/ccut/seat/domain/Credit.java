package cn.ccut.seat.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "user_credit")
public class Credit {
    @Id
    @Column(name = "user_id")
    private Long userId;

    private Integer score = 100;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
}
