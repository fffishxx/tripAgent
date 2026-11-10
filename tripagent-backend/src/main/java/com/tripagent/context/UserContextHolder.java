package com.tripagent.context;

import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;

/**
 * 用户上下文 ThreadLocal 容器；必须在请求结束时调用 clear()。
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        UserContext context = HOLDER.get();
        return context == null ? null : context.userId();
    }

    public static Long getRequiredUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
