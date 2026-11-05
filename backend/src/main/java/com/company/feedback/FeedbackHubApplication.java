package com.company.feedback;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 项目启动类
 *
 * <p><b>@MapperScan 的作用</b>：
 * 告诉 MyBatis 去哪个包下扫描 Mapper 接口并生成代理实现。
 * 如果不加这个注解，每个 Mapper 接口上都得单独加 @Mapper 注解，
 * 批量扫描更省事也更不容易漏。
 *
 * <p><b>@ConfigurationPropertiesScan 的作用</b>：
 * 自动扫描并注册 {@code @ConfigurationProperties} 配置类，
 * 省去在启动类上逐个 {@code @EnableConfigurationProperties} 的麻烦。
 *
 * @author 反馈改进助手项目组
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.company.feedback.config")
@MapperScan("com.company.feedback.mapper")
public class FeedbackHubApplication {

    /**
     * 程序入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FeedbackHubApplication.class, args);
    }
}
