package com.tripagent.common.exception;

import com.tripagent.common.result.Result;
import com.tripagent.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局异常处理：所有异常都转换为统一响应，不向客户端泄露堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Object>> handleBusiness(BusinessException ex) {
        log.warn("业务异常：业务码={}，信息={}",
                ex.getResultCode().getCode(), ex.getMessage());
        Result<Object> body = Result.failure(ex.getResultCode(), ex.getMessage());
        return ResponseEntity.status(ex.getResultCode().getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, Object>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        return validationResponseFromFieldErrors(ex.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Map<String, Object>>> handleBind(BindException ex) {
        return validationResponseFromFieldErrors(ex.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Map<String, Object>>> handleConstraintViolation(
            ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(violation -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("field", violation.getPropertyPath().toString());
                    item.put("message", violation.getMessage());
                    return item;
                })
                .toList();
        return validationResponseFromErrors(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Object>> handleMissingParameter(
            MissingServletRequestParameterException ex) {
        String message = "缺少请求参数：" + ex.getParameterName();
        log.warn(message);
        return badRequest(message);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Object>> handleMissingHeader(MissingRequestHeaderException ex) {
        String message = "缺少请求头：" + ex.getHeaderName();
        log.warn(message);
        return badRequest(message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "参数格式错误：" + ex.getName();
        log.warn(message);
        return badRequest(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("请求体无法解析：{}", ex.getMessage());
        return badRequest("请求体格式错误");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        String message = "请求方法不支持：" + ex.getMethod();
        log.warn(message);
        Result<Object> body = Result.failure(ResultCode.METHOD_NOT_ALLOWED, message);
        return ResponseEntity.status(ResultCode.METHOD_NOT_ALLOWED.getHttpStatus()).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Object>> handleNoResource(NoResourceFoundException ex) {
        log.warn("资源不存在：{}", ex.getResourcePath());
        Result<Object> body = Result.failure(ResultCode.NOT_FOUND);
        return ResponseEntity.status(ResultCode.NOT_FOUND.getHttpStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleUnknown(Exception ex) {
        log.error("未知异常", ex);
        Result<Object> body = Result.failure(ResultCode.INTERNAL_ERROR);
        return ResponseEntity.status(ResultCode.INTERNAL_ERROR.getHttpStatus()).body(body);
    }

    private ResponseEntity<Result<Map<String, Object>>> validationResponseFromFieldErrors(
            List<FieldError> fieldErrors) {
        List<Map<String, String>> errors = fieldErrors.stream()
                .map(fieldError -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("field", fieldError.getField());
                    item.put("message", fieldError.getDefaultMessage());
                    return item;
                })
                .toList();
        return validationResponseFromErrors(errors);
    }

    private ResponseEntity<Result<Map<String, Object>>> validationResponseFromErrors(
            List<Map<String, String>> errors) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errors", errors);
        log.warn("参数校验失败：{}", errors);
        Result<Map<String, Object>> body =
                Result.failureWithData(ResultCode.VALIDATION_ERROR, data);
        return ResponseEntity.status(ResultCode.VALIDATION_ERROR.getHttpStatus()).body(body);
    }

    private ResponseEntity<Result<Object>> badRequest(String message) {
        Result<Object> body = Result.failure(ResultCode.BAD_REQUEST, message);
        return ResponseEntity.status(ResultCode.BAD_REQUEST.getHttpStatus()).body(body);
    }
}
