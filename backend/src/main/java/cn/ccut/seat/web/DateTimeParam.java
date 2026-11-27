package cn.ccut.seat.web;

import java.time.LocalDateTime;

/**
 * 入参日期时间解析：把缺失或格式错误统一转成业务异常（400），
 * 避免非法字符串直接落到兜底处理器返回 500。
 */
public final class DateTimeParam {

    private DateTimeParam() {
    }

    public static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException("PARAM_INVALID", "时间参数缺失");
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException e) {
            throw new BizException("PARAM_INVALID", "时间格式不正确，请使用标准日期时间格式");
        }
    }
}
