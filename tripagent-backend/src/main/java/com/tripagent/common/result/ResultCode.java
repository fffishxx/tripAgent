package com.tripagent.common.result;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一业务错误码。
 *
 * <p>code 为业务码，HTTP 状态码由 httpStatus 决定；两者共同表达错误语义。</p>
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "ok", HttpStatus.OK),

    BAD_REQUEST(40000, "请求参数错误", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(40001, "参数校验失败", HttpStatus.BAD_REQUEST),
    TRIP_DATES_REQUIRED(40002, "保存行程需要提供开始和结束日期", HttpStatus.BAD_REQUEST),

    UNAUTHORIZED(40101, "未认证或登录已过期", HttpStatus.UNAUTHORIZED),

    FORBIDDEN(40301, "无权限", HttpStatus.FORBIDDEN),
    SERVICE_AUTH_FAILED(40302, "服务身份校验失败", HttpStatus.FORBIDDEN),

    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    TRIP_NOT_FOUND(40401, "行程不存在或无权访问", HttpStatus.NOT_FOUND),
    FAVORITE_NOT_FOUND(40402, "收藏不存在或无权访问", HttpStatus.NOT_FOUND),
    ORDER_NOT_FOUND(40403, "订单不存在或无权访问", HttpStatus.NOT_FOUND),

    METHOD_NOT_ALLOWED(40500, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),

    ACCOUNT_EXISTS(40901, "手机号或邮箱已存在", HttpStatus.CONFLICT),
    VERSION_CONFLICT(40902, "数据已变更，请刷新后重试", HttpStatus.CONFLICT),
    ORDER_STATE_CONFLICT(40903, "订单状态不允许该操作", HttpStatus.CONFLICT),
    IDEMPOTENT_RESOURCE_DELETED(40905, "幂等键对应的行程已删除，请使用新的幂等键", HttpStatus.CONFLICT),

    INTERNAL_ERROR(50000, "服务内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ResultCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
