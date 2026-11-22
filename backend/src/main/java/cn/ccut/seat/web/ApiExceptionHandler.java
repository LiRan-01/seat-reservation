package cn.ccut.seat.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> biz(BizException e) {
        return body(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> illegal(IllegalArgumentException e) {
        return body(e.getMessage(), "参数或令牌无效");
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> conflict(Exception e) {
        // 并发抢座时唯一索引兜底生效，统一按时段冲突返回，避免暴露 500
        return body("TIME_CONFLICT", "该时段座位已被预约");
    }

    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> busy(Exception e) {
        return body("BUSY", "并发争用，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> other(Exception e) {
        return body("SERVER_ERROR", e.getMessage() == null ? "服务异常" : e.getMessage());
    }

    private Map<String, Object> body(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        m.put("data", null);
        return m;
    }
}
