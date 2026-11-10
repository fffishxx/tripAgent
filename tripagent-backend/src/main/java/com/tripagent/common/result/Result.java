package com.tripagent.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tripagent.common.constant.CommonConstants;
import lombok.Getter;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一响应结构。
 *
 * <pre>
 * {
 *   "code": 0,
 *   "message": "ok",
 *   "data": {},
 *   "requestId": "...",
 *   "timestamp": "2026-11-06 15:30:00"
 * }
 * </pre>
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public class Result<T> {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final int code;
    private final String message;
    private final T data;
    private final String requestId;
    private final String timestamp;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = MDC.get(CommonConstants.MDC_REQUEST_ID);
        this.timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> failure(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> Result<T> failure(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    public static <T> Result<T> failureWithData(ResultCode resultCode, T data) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), data);
    }

    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null);
    }
}
