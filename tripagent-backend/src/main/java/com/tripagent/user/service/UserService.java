package com.tripagent.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.preference.service.PreferenceService;
import com.tripagent.security.JwtService;
import com.tripagent.security.JwtToken;
import com.tripagent.user.dto.LoginRequest;
import com.tripagent.user.dto.RegisterRequest;
import com.tripagent.user.dto.UpdateProfileRequest;
import com.tripagent.user.entity.User;
import com.tripagent.user.mapper.UserMapper;
import com.tripagent.user.vo.LoginVO;
import com.tripagent.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PreferenceService preferenceService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterRequest request) {
        String phone = request.getPhone().trim();
        String email = trimToNull(request.getEmail());

        if (userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getPhone, phone)) > 0) {
            throw new BusinessException(ResultCode.ACCOUNT_EXISTS, "手机号已注册");
        }
        if (email != null
                && userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getEmail, email)) > 0) {
            throw new BusinessException(ResultCode.ACCOUNT_EXISTS, "邮箱已被使用");
        }

        User user = new User();
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(defaultNickname(request.getNickname(), phone));
        user.setUsername(null);
        user.setEmail(email);
        user.setStatus(1);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.ACCOUNT_EXISTS, "手机号或邮箱已存在");
        }

        // 注册与默认偏好同一事务
        preferenceService.createDefault(user.getId());
        return UserVO.from(user);
    }

    public LoginVO login(LoginRequest request) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getPhone, request.getPhone().trim()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "手机号或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        LocalDateTime now = LocalDateTime.now();
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getLastLoginTime, now));
        user.setLastLoginTime(now);

        JwtToken token = jwtService.generateToken(user);
        return LoginVO.builder()
                .accessToken(token.accessToken())
                .tokenType(token.tokenType())
                .expiresIn(token.expiresIn())
                .user(UserVO.from(user))
                .build();
    }

    public UserVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return UserVO.from(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        if (request.getNickname() != null) {
            String nickname = trimToNull(request.getNickname());
            user.setNickname(nickname == null ? defaultNickname(null, user.getPhone()) : nickname);
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        }
        if (request.getEmail() != null) {
            String email = trimToNull(request.getEmail());
            if (email != null) {
                long count = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                        .eq(User::getEmail, email)
                        .ne(User::getId, userId));
                if (count > 0) {
                    throw new BusinessException(ResultCode.ACCOUNT_EXISTS, "邮箱已被使用");
                }
            }
            user.setEmail(email);
        }

        userMapper.updateById(user);
        return UserVO.from(user);
    }

    private String defaultNickname(String nickname, String phone) {
        if (StringUtils.hasText(nickname)) {
            return nickname.trim();
        }
        String suffix = phone != null && phone.length() >= 4
                ? phone.substring(phone.length() - 4)
                : "";
        return "用户" + suffix;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
