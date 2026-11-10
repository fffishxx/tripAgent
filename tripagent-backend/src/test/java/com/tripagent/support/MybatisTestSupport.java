package com.tripagent.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 纯单元测试中初始化 MyBatis-Plus 的 Lambda 列缓存。
 */
public final class MybatisTestSupport {

    private MybatisTestSupport() {
    }

    public static void initTableInfo(Class<?>... entities) {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : entities) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }
}
