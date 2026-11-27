package cn.ccut.seat.web;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DateTimeParam 单元测试：日期入参缺失或非法时统一转为参数错误。
 */
class DateTimeParamTest {

    @Test
    void parsesValidValue() {
        LocalDateTime t = DateTimeParam.parse("2026-11-27T08:00:00");
        assertEquals(2026, t.getYear());
        assertEquals(8, t.getHour());
    }

    @Test
    void throwsWhenNull() {
        BizException ex = assertThrows(BizException.class, () -> DateTimeParam.parse(null));
        assertEquals("PARAM_INVALID", ex.getCode());
    }

    @Test
    void throwsWhenBlank() {
        BizException ex = assertThrows(BizException.class, () -> DateTimeParam.parse("   "));
        assertEquals("PARAM_INVALID", ex.getCode());
    }

    @Test
    void throwsWhenMalformed() {
        BizException ex = assertThrows(BizException.class, () -> DateTimeParam.parse("2026/11/27 08:00"));
        assertEquals("PARAM_INVALID", ex.getCode());
    }
}
