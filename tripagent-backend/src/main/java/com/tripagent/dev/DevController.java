package com.tripagent.dev;

import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.Result;
import com.tripagent.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 阶段二验收用接口，仅在 dev profile 生效。
 */
@RestController
@RequestMapping("/api/v1/dev")
@Profile("dev")
@RequiredArgsConstructor
@Tag(name = "开发验证", description = "仅 dev profile 生效，用于阶段二验收")
public class DevController {

    private final DevMapperService devMapperService;

    @GetMapping("/mapper-crud")
    @Operation(summary = "MyBatis-Plus CRUD 验证", description = "插入/查询/更新/删除一个测试用户，事务回滚")
    public Result<Map<String, Object>> mapperCrud() {
        return Result.success(devMapperService.runCrudTest());
    }

    @PostMapping("/validate")
    @Operation(summary = "参数校验验证", description = "name 必填，days 1~30")
    public Result<String> validate(@Valid @RequestBody DevValidateRequest request) {
        return Result.success("校验通过");
    }

    @GetMapping("/error")
    @Operation(summary = "业务异常验证", description = "抛出业务异常，验证全局异常处理")
    public Result<Void> error() {
        throw new BusinessException(ResultCode.TRIP_NOT_FOUND);
    }

    @GetMapping("/error-500")
    @Operation(summary = "未知异常验证", description = "抛出未知异常，验证 500 响应不泄露堆栈")
    public Result<Void> error500() {
        throw new IllegalStateException("dev simulated error");
    }
}
