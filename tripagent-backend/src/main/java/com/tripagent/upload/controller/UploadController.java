package com.tripagent.upload.controller;

import com.tripagent.common.result.Result;
import com.tripagent.context.UserContextHolder;
import com.tripagent.upload.service.OssUploadService;
import com.tripagent.upload.vo.AvatarUploadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
@Tag(name = "文件上传", description = "服务端代理上传头像到阿里云 OSS")
public class UploadController {

    private final OssUploadService ossUploadService;

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传头像", description = "最大 5MB，支持 JPG/PNG/WEBP/GIF；返回公开访问 URL")
    public Result<AvatarUploadVO> uploadAvatar(
            @RequestPart("file") MultipartFile file) {
        return Result.success(ossUploadService.uploadAvatar(
                UserContextHolder.getRequiredUserId(), file));
    }
}
