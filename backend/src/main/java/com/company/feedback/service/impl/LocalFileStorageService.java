package com.company.feedback.service.impl;

import com.company.feedback.common.ResultCode;
import com.company.feedback.config.StorageProperties;
import com.company.feedback.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘文件存储实现
 *
 * <p>生效条件：{@code storage.type=local}（默认值）。
 *
 * <p><b>为什么默认用这个实现</b>：零外部依赖、断网可用、无需密钥。
 * 文件写入本地目录后，由 Nginx 直接代理该目录对外提供访问，
 * 不经过 Spring 应用转发，静态资源性能更好。
 *
 * <p><b>注意</b>：写入的是本地磁盘，多实例部署时无法共享。
 * 本项目为单机部署，不构成问题；若将来要多实例，
 * 切换到 OSS 实现即可。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService extends AbstractFileStorageService {

    private final StorageProperties properties;

    /**
     * 上传文件到本地磁盘
     *
     * @param file    上传的文件
     * @param bizType 业务类型（作为一级子目录）
     * @return 上传结果
     */
    @Override
    public StoredFile upload(MultipartFile file, String bizType) {

        String ext = validate(file);
        String objectKey = buildObjectKey(bizType, ext);

        Path root = Paths.get(properties.getLocal().getRootPath())
                .toAbsolutePath()
                .normalize();
        Path target = root.resolve(objectKey).normalize();

        // 防御性校验：确保解析后的路径仍在根目录内。
        // 虽然 objectKey 由系统生成、不含 .. ，
        // 但路径安全这类事最好在写入前再确认一次，成本极低。
        if (!target.startsWith(root)) {
            throw new BizException(ResultCode.BAD_REQUEST, "非法的文件路径");
        }

        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("本地文件写入失败, objectKey={}", objectKey, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件保存失败，请稍后重试");
        }

        String url = properties.getLocal().getUrlPrefix() + objectKey;
        log.info("本地文件上传成功, objectKey={}, size={}", objectKey, file.getSize());

        return new StoredFile(objectKey, url);
    }

    /**
     * 删除本地文件
     *
     * @param objectKey 对象键
     * @return 删除成功返回 true
     */
    @Override
    public boolean delete(String objectKey) {

        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }

        Path root = Paths.get(properties.getLocal().getRootPath())
                .toAbsolutePath()
                .normalize();
        Path target = root.resolve(objectKey).normalize();

        if (!target.startsWith(root)) {
            log.warn("拒绝删除根目录外的文件, objectKey={}", objectKey);
            return false;
        }

        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            log.error("本地文件删除失败, objectKey={}", objectKey, e);
            return false;
        }
    }
}
