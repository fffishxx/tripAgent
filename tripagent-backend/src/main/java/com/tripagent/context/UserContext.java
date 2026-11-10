package com.tripagent.context;

/**
 * 当前登录用户上下文，阶段三由用户 JWT 拦截器写入。
 */
public record UserContext(Long userId, String phone, String nickname) {
}
