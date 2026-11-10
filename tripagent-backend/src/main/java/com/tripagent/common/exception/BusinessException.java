package com.tripagent.common.exception;

import com.tripagent.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常，由全局异常处理器转换为统一响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String message, Throwable cause) {
        super(message, cause);
        this.resultCode = resultCode;
    }
}
