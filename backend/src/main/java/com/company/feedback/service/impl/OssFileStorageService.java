package com.company.feedback.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.company.feedback.common.ResultCode;
import com.company.feedback.config.StorageProperties;
import com.company.feedback.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 阿里云 OSS 文件存储实现
 *
 * <p>生效条件：{@code storage.type=oss}。
 * 切换到本实现只需改一行配置，业务代码无需任何改动——
 * 这正是「存储抽象层」的价值。
 *
 * <p><b>实现上的两点取舍</b>：
 * <ol>
 *   <li><b>懒创建客户端</b>：不在构造时建立连接，
 *       避免密钥未配置就导致应用启动失败。</li>
 *   <li><b>每次用完立即关闭</b>：OSSClient 是重量级对象，
 *       本项目的上传频率很低（人工录入反馈），
 *       用短连接换取"不会泄漏连接池"的确定性更划算。</li>
 * </ol>
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "oss")
public class OssFileStorageService extends AbstractFileStorageService {

    private final StorageProperties properties;

    /**
     * 构造注入配置
     *
     * <p>这里刻意不创建 OSSClient：
     * 如果密钥是占位符，创建客户端会在启动期抛异常，
     * 整个应用起不来。改为上传时才创建，配置有问题只影响上传功能。
     *
     * @param properties 存储配置
     */
    public OssFileStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    /**
     * 上传文件到 OSS
     *
     * @param file    上传的文件
     * @param bizType 业务类型（作为对象键前缀）
     * @return 上传结果
     */
    @Override
    public StoredFile upload(MultipartFile file, String bizType) {

        String ext = validate(file);
        String objectKey = buildObjectKey(bizType, ext);

        StorageProperties.Oss cfg = properties.getOss();
        requireConfigured(cfg);

        OSS client = new OSSClientBuilder().build(
                cfg.getEndpoint(), cfg.getAccessKeyId(), cfg.getAccessKeySecret());

        try (InputStream in = file.getInputStream()) {

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            // 显式设置 Content-Type，否则浏览器会当成二进制流下载而不是直接预览
            if (file.getContentType() != null) {
                metadata.setContentType(file.getContentType());
            }

            client.putObject(cfg.getBucketName(), objectKey, in, metadata);

        } catch (IOException e) {
            log.error("OSS 文件读取失败, objectKey={}", objectKey, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件读取失败，请稍后重试");
        } catch (Exception e) {
            // OSS SDK 抛的异常类型很多（网络、鉴权、Bucket 不存在等），
            // 这里统一转成业务异常，避免把云厂商的堆栈暴露给前端
            log.error("OSS 上传失败, objectKey={}", objectKey, e);
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "文件上传到云存储失败，请检查网络或改用本地存储");
        } finally {
            client.shutdown();
        }

        String url = cfg.getUrlPrefix() + objectKey;
        log.info("OSS 文件上传成功, objectKey={}, size={}", objectKey, file.getSize());

        return new StoredFile(objectKey, url);
    }

    /**
     * 删除 OSS 对象
     *
     * @param objectKey 对象键
     * @return 删除成功返回 true
     */
    @Override
    public boolean delete(String objectKey) {

        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }

        StorageProperties.Oss cfg = properties.getOss();
        requireConfigured(cfg);

        OSS client = new OSSClientBuilder().build(
                cfg.getEndpoint(), cfg.getAccessKeyId(), cfg.getAccessKeySecret());

        try {
            client.deleteObject(cfg.getBucketName(), objectKey);
            return true;
        } catch (Exception e) {
            log.error("OSS 删除失败, objectKey={}", objectKey, e);
            return false;
        } finally {
            client.shutdown();
        }
    }

    /**
     * 校验 OSS 配置是否可用
     *
     * <p>提前拦截占位符配置，给出可操作的错误提示，
     * 而不是让用户看到一段 SDK 的鉴权失败堆栈。
     *
     * @param cfg OSS 配置
     */
    private void requireConfigured(StorageProperties.Oss cfg) {
        if (isPlaceholder(cfg.getEndpoint())
                || isPlaceholder(cfg.getAccessKeyId())
                || isPlaceholder(cfg.getAccessKeySecret())
                || isPlaceholder(cfg.getBucketName())) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "OSS 尚未配置：请在 application.yml 填入 AccessKey 与 Bucket，"
                            + "或把 storage.type 改为 local 使用本地存储");
        }
    }

    /**
     * 判断配置值是否仍是占位符
     *
     * @param value 配置值
     * @return 是占位符或空值返回 true
     */
    private boolean isPlaceholder(String value) {
        return value == null || value.isBlank() || value.contains("YOUR_");
    }
}
