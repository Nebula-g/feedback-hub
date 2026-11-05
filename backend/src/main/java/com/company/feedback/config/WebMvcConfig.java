package com.company.feedback.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置
 *
 * <p><b>本类只做一件事</b>：把本地存储目录映射成可访问的静态资源路径。
 *
 * <p><b>为什么需要它</b>：
 * 上传接口把文件写到磁盘后返回了一个 URL，但如果没人处理这个 URL，
 * 浏览器访问时只会得到 404。生产环境通常由 Nginx 直接代理该目录，
 * 但在本机开发、或者没装 Nginx 的机器上就跑不通。
 * 加上这个映射，图片在"只有后端"的环境里也能正常显示，
 * 降低联调门槛。
 *
 * <p>生产部署时 Nginx 也会代理 {@code /uploads/}，
 * 静态文件由 Nginx 直出、不经过 Java 应用，性能更好。
 * 两者不冲突：只是多了一条可用通道。
 *
 * @author 反馈改进助手项目组
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    /**
     * 注册静态资源处理器
     *
     * @param registry 资源注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // 只有本地存储模式才需要映射磁盘目录；
        // OSS 模式下图片由云端直出，本地没有对应文件
        if (!"local".equalsIgnoreCase(storageProperties.getType())) {
            return;
        }

        Path root = Paths.get(storageProperties.getLocal().getRootPath())
                .toAbsolutePath()
                .normalize();

        // file: 前缀表示"磁盘路径"而非 classpath；
        // 结尾必须带斜杠，否则 Spring 会把它当成单个文件而不是目录
        String location = "file:" + root.toString().replace('\\', '/') + "/";

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
