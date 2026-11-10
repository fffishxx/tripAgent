package com.tripagent.dev;

import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.user.entity.User;
import com.tripagent.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 仅 dev profile 生效的 MyBatis-Plus CRUD 验证。
 *
 * <p>方法带事务，验证完成后回滚，不留下测试数据。</p>
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevMapperService {

    private final UserMapper userMapper;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runCrudTest() {
        String phone = "199" + String.format("%08d",
                ThreadLocalRandom.current().nextInt(100_000_000));
        String username = "dev_" + UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setPhone(phone);
        user.setPasswordHash("dev-test");
        user.setNickname("dev");
        user.setUsername(username);
        user.setStatus(1);

        int inserted = userMapper.insert(user);
        if (inserted != 1 || user.getId() == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "MyBatis-Plus insert 失败");
        }

        User selected = userMapper.selectById(user.getId());
        if (selected == null || !phone.equals(selected.getPhone())) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "MyBatis-Plus selectById 失败");
        }

        selected.setNickname("dev-updated");
        int updated = userMapper.updateById(selected);
        if (updated != 1) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "MyBatis-Plus updateById 失败");
        }

        User updatedUser = userMapper.selectById(user.getId());
        if (updatedUser == null || !"dev-updated".equals(updatedUser.getNickname())) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "MyBatis-Plus 更新后查询不一致");
        }

        int deleted = userMapper.deleteById(user.getId());
        if (deleted != 1) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "MyBatis-Plus deleteById 失败");
        }
        if (userMapper.selectById(user.getId()) != null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "MyBatis-Plus 删除后仍能查询到数据");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phone", phone);
        result.put("insertedId", String.valueOf(user.getId()));
        result.put("insert", true);
        result.put("select", true);
        result.put("update", true);
        result.put("delete", true);
        result.put("rollback", "测试方法使用 @Transactional 并标记回滚，验证数据不会落库");
        // CRUD 已全部验证通过；显式标记回滚，避免测试数据落库
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        log.info("MyBatis-Plus CRUD 验证通过，手机号={}", phone);
        return result;
    }
}
