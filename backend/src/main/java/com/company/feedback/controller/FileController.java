package com.company.feedback.controller;

import com.company.feedback.common.R;
import com.company.feedback.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传控制器
 *
 * <p><b>用途</b>：反馈录入时上传客户提供的截图证据。
 * 图片是「反馈原文」的一部分，也是答辩时证明
 * "系统展示的是真实用户原话"的关键材料。
 *
 * <p><b>权限说明</b>：任何已登录用户都能上传。
 * 客服录入反馈时要传图，PM 补充材料时也要传图，
 * 限制得过严反而影响正常使用。
 * 真正的风险由格式白名单 + 大小限制 + 随机文件名三道防线控制。
 *
 * <p><b>注意</b>：本接口只负责"把文件存起来并返回 URL"，
 * 不写数据库。URL 由调用方随反馈一起提交，
 * 这样"上传了但没提交表单"不会留下孤儿记录。
 *
 * @author 反馈改进助手项目组
 */
@Tag(name = "文件上传", description = "反馈截图上传（本地磁盘 / 阿里云 OSS 可切换）")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /**
     * 上传单个文件
     *
     * @param file    上传的文件（表单字段名 file）
     * @param bizType 业务类型，决定存储子目录，默认 feedback
     * @return 包含 url 与 objectKey 的结果
     */
    @Operation(summary = "上传图片",
            description = "支持 jpg/jpeg/png/gif/webp/bmp，单文件不超过 10MB，返回可访问 URL")
    @PostMapping("/upload")
    public R<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", required = false, defaultValue = "feedback")
            String bizType) {

        FileStorageService.StoredFile stored = fileStorageService.upload(file, bizType);

        return R.ok(Map.of(
                "url", stored.url(),
                "objectKey", stored.objectKey()
        ));
    }
}
