package com.company.feedback.service.impl;

import com.company.feedback.common.ResultCode;
import com.company.feedback.exception.BizException;
import com.company.feedback.service.FileStorageService;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件存储抽象基类
 *
 * <p><b>把"与本项目业务相关的规则"收在这一层</b>：
 * 允许哪些格式、单个文件多大、对象键怎么命名。
 * 这些规则和"存到本地还是存到 OSS"无关，
 * 放到基类里两种实现自动共享，不会出现
 * "换一个存储介质就忘了校验文件类型"这种漏洞。
 *
 * <p>子类只需要关心真正的 I/O 动作。
 *
 * @author 反馈改进助手项目组
 */
public abstract class AbstractFileStorageService implements FileStorageService {

    /** 允许上传的图片格式：只放行图片，避免被当成任意文件上传通道 */
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    /** 单文件大小上限：10MB，与 application.yml 的 multipart 限制保持一致 */
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    /** 对象键中的日期目录格式：按天分目录，避免单目录文件过多导致遍历变慢 */
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * 校验上传文件
     *
     * <p>三条规则：非空、大小在限、扩展名在白名单内。
     * 任何一条不满足都抛业务异常，由全局异常处理器统一转成 400 响应。
     *
     * @param file 上传的文件
     * @return 规范化后的扩展名（小写，不含点）
     */
    protected String validate(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "上传文件不能为空");
        }

        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "文件大小超过限制（最大 10MB）");
        }

        String originalName = file.getOriginalFilename();
        String ext = extractExtension(originalName);

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "仅支持图片格式：" + String.join(" / ", ALLOWED_EXTENSIONS));
        }

        return ext;
    }

    /**
     * 生成对象键
     *
     * <p>格式：{@code bizType/yyyy/MM/dd/UUID.ext}
     * <ul>
     *   <li>用 UUID 而非原始文件名：避免中文名、重名、路径穿越（../）等一堆问题</li>
     *   <li>按日期分目录：便于按时间归档与清理</li>
     * </ul>
     *
     * @param bizType 业务类型，为空时归入 common
     * @param ext     文件扩展名
     * @return 对象键
     */
    protected String buildObjectKey(String bizType, String ext) {
        String dir = (bizType == null || bizType.isBlank())
                ? "common"
                : bizType.trim().toLowerCase(Locale.ROOT);
        // 业务类型只允许字母数字下划线，防止通过 bizType 参数做路径穿越
        if (!dir.matches("[a-z0-9_]+")) {
            dir = "common";
        }
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return dir + "/" + LocalDate.now().format(DATE_DIR) + "/" + uuid + "." + ext;
    }

    /**
     * 从原始文件名中提取小写扩展名
     *
     * @param originalName 原始文件名
     * @return 扩展名；无扩展名时返回空串
     */
    private String extractExtension(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
