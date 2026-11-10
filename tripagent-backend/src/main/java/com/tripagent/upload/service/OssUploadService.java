package com.tripagent.upload.service;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.upload.config.OssProperties;
import com.tripagent.upload.vo.AvatarUploadVO;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssUploadService {

    private static final Map<String, String> AVATAR_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    private final OssProperties ossProperties;

    private volatile OSS ossClient;

    public AvatarUploadVO uploadAvatar(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        validateConfiguration();
        validateFile(file);

        String contentType = normalizeContentType(file.getContentType());
        String extension = AVATAR_TYPES.get(contentType);
        if (extension == null) {
            throw validation("头像仅支持 JPG、PNG、WEBP 或 GIF 图片");
        }

        String objectKey = buildObjectKey(userId, extension);
        OSS client = getClient();

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType);
            metadata.setObjectAcl(CannedAccessControlList.PublicRead);

            PutObjectRequest request = new PutObjectRequest(
                    ossProperties.getBucket(), objectKey, inputStream, metadata);
            client.putObject(request);

            String url = buildPublicUrl(objectKey);
            log.info("头像上传成功：userId={}，key={}，size={}", userId, objectKey, file.getSize());
            return new AvatarUploadVO(url, objectKey);
        } catch (OSSException | ClientException e) {
            log.error("头像上传到 OSS 失败：userId={}，key={}", userId, objectKey, e);
            throw new BusinessException(
                    ResultCode.INTERNAL_ERROR, "头像上传失败，请稍后重试");
        } catch (Exception e) {
            log.error("头像读取或上传失败：userId={}，key={}", userId, objectKey, e);
            throw new BusinessException(
                    ResultCode.INTERNAL_ERROR, "头像上传失败，请稍后重试");
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(ossProperties.getEndpoint())
                || !StringUtils.hasText(ossProperties.getBucket())
                || !StringUtils.hasText(ossProperties.getAccessKeyId())
                || !StringUtils.hasText(ossProperties.getAccessKeySecret())) {
            throw new BusinessException(
                    ResultCode.INTERNAL_ERROR,
                    "头像上传服务未配置，请设置 OSS_ENDPOINT、OSS_BUCKET 和 OSS 访问凭据");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw validation("请选择要上传的头像图片");
        }
        if (file.getSize() > ossProperties.getMaxAvatarBytes()) {
            throw validation("头像不能超过 5MB");
        }
    }

    private String normalizeContentType(String contentType) {
        return contentType == null
                ? ""
                : contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
    }

    private String buildObjectKey(Long userId, String extension) {
        String prefix = StringUtils.hasText(ossProperties.getAvatarPrefix())
                ? ossProperties.getAvatarPrefix().trim().replaceAll("^/+|/+$", "")
                : "avatars";
        return prefix + "/" + userId + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + "." + extension;
    }

    private String buildPublicUrl(String objectKey) {
        if (StringUtils.hasText(ossProperties.getPublicBaseUrl())) {
            return trimTrailingSlash(ossProperties.getPublicBaseUrl().trim()) + "/" + objectKey;
        }

        String endpoint = ossProperties.getEndpoint().trim();
        String scheme = "https";
        if (endpoint.startsWith("http://")) {
            scheme = "http";
            endpoint = endpoint.substring("http://".length());
        } else if (endpoint.startsWith("https://")) {
            endpoint = endpoint.substring("https://".length());
        }
        endpoint = trimTrailingSlash(endpoint);
        return scheme + "://" + ossProperties.getBucket().trim() + "." + endpoint + "/" + objectKey;
    }

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private OSS getClient() {
        OSS current = ossClient;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (ossClient == null) {
                ossClient = new OSSClientBuilder().build(
                        ossProperties.getEndpoint().trim(),
                        ossProperties.getAccessKeyId().trim(),
                        ossProperties.getAccessKeySecret());
            }
            return ossClient;
        }
    }

    private BusinessException validation(String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }

    @PreDestroy
    public void shutdown() {
        OSS current = ossClient;
        if (current != null) {
            current.shutdown();
        }
    }
}
