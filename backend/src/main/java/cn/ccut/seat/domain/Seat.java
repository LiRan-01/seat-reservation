package cn.ccut.seat.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "seat")
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id")
    private Long roomId;

    private String code;

    /** 靠窗 / 有插座 / 靠入口 等特征，逗号分隔 */
    private String feature;

    @Enumerated(EnumType.STRING)
    private SeatStatus status = SeatStatus.AVAILABLE;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }
    public SeatStatus getStatus() { return status; }
    public void setStatus(SeatStatus status) { this.status = status; }
}
