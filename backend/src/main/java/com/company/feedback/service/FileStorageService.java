package com.company.feedback.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口
 *
 * <p><b>设计意图：把"存到哪"和"怎么用"解耦</b>。
 * 业务层（反馈录入）只关心"上传成功后得到一个可访问的 URL"，
 * 不关心文件是躺在本地磁盘还是阿里云 OSS。
 * 这样切换存储介质时，业务代码一行都不用改。
 *
 * <p>实现类通过 {@code @ConditionalOnProperty} 按 {@code storage.type} 装配，
 * Spring 容器里同时只会存在一个实现。
 *
 * @author 反馈改进助手项目组
 */
public interface FileStorageService {

    /**
     * 上传文件
     *
     * @param file    上传的文件
     * @param bizType 业务类型（用于分目录，如 feedback），可为空
     * @return 上传结果，包含可访问 URL 与对象键
     */
    StoredFile upload(MultipartFile file, String bizType);

    /**
     * 删除文件
     *
     * <p>本项目的反馈图片不允许业务层删除（原文不可篡改），
     * 该方法仅供管理员清理误传文件时使用。
     *
     * @param objectKey 对象键（upload 返回的值）
     * @return 删除成功返回 true
     */
    boolean delete(String objectKey);

    /**
     * 上传结果
     *
     * @param objectKey 对象键，用于后续删除或迁移
     * @param url       可直接在浏览器访问的完整地址
     */
    record StoredFile(String objectKey, String url) {
    }
}
