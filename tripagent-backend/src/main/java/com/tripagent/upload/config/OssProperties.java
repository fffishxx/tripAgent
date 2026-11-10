package com.tripagent.upload.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 服务端配置。
 *
 * <p>AccessKey 只能从环境变量读取，禁止写入代码、前端或仓库。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    private String endpoint = "";

    private String bucket = "";

    private String accessKeyId = "";

    private String accessKeySecret = "";

    /**
     * 可选。比如 CDN/自定义域名；为空时使用标准 bucket 域名。
     */
    private String publicBaseUrl = "";

    private String avatarPrefix = "avatars";

    private long maxAvatarBytes = 5 * 1024 * 1024L;
}
