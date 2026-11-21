package cn.ccut.seat.domain;

public enum SeatStatus {
    AVAILABLE,  // 空闲
    RESERVED,   // 已预约未签到
    USING,      // 使用中
    AWAY,       // 暂离
    DISABLED    // 停用
}
