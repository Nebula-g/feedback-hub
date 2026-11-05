package com.company.feedback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储配置
 *
 * <p>对应 {@code application.yml} 中的 {@code storage} 前缀。
 *
 * <p><b>为什么用「抽象层 + 双实现」而不是直接用 OSS</b>：
 * 演示环境的网络、密钥、Bucket 权限都是不可控因素。
 * 一旦答辩现场 OSS 不可用，图片功能就整体瘫痪，
 * 而截图恰恰是「反馈原文」最重要的证据之一。
 * 因此默认用本地磁盘（零依赖、断网可用），
 * OSS 作为第二实现，改一行配置即可切换。
 *
 * <p><b>这不是"放弃 OSS"</b>：OSS 实现是完整可用的，
 * 填入真实密钥后 {@code storage.type=oss} 即可切换到云端存储。
 *
 * @author 反馈改进助手项目组
 */
@Data
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /**
     * 存储类型：local-本地磁盘 / oss-阿里云对象存储
     */
    private String type = "local";

    /** 本地存储配置 */
    private Local local = new Local();

    /** 阿里云 OSS 配置 */
    private Oss oss = new Oss();

    /**
     * 本地存储配置项
     */
    @Data
    public static class Local {

        /** 文件存放根目录（绝对路径） */
        private String rootPath = "./uploads";

        /** 对外访问前缀（由 Nginx 代理到 rootPath） */
        private String urlPrefix = "http://localhost:8080/uploads/";
    }

    /**
     * 阿里云 OSS 配置项
     */
    @Data
    public static class Oss {

        /** OSS 服务地址，如 oss-cn-hangzhou.aliyuncs.com */
        private String endpoint;

        /** AccessKey ID */
        private String accessKeyId;

        /** AccessKey Secret */
        private String accessKeySecret;

        /** Bucket 名称 */
        private String bucketName;

        /** 对外访问前缀 */
        private String urlPrefix;
    }
}
