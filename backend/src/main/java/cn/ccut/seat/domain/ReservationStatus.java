package cn.ccut.seat.domain;

public enum ReservationStatus {
    RESERVED,   // 已预约，等待签到
    USING,      // 使用中
    AWAY,       // 暂离中
    RELEASED,   // 正常释放
    NO_SHOW,    // 爽约（未签到 / 暂离超时）
    FORCED,     // 管理员强制释放
    CANCELLED   // 用户主动取消
}
