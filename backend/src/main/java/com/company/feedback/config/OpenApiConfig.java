package com.company.feedback.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi（Swagger UI）配置
 *
 * <p><b>为什么用 springdoc 而不是 springfox</b>：
 * springfox 自 2020 年起停止维护，且不支持 Spring Boot 3 的 Jakarta 命名空间。
 * springdoc-openapi 2.x 是目前 Boot 3 的标准方案。
 *
 * <p>本配置额外声明了 Bearer Token 认证方式，
 * 配置完成后 Swagger UI 右上角会出现"Authorize"按钮，
 * 填入 Token 即可直接调试需要鉴权的接口，无需每次手动加请求头。
 *
 * @author 反馈改进助手项目组
 */
@Configuration
public class OpenApiConfig {

    /** 安全方案名称，需与 SecurityRequirement 中一致 */
    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    /**
     * 定义 OpenAPI 文档元信息与认证方式
     *
     * @return OpenAPI 描述对象
     */
    @Bean
    public OpenAPI feedbackHubOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("企业报销软件用户反馈与功能改进助手 API")
                        .version("v1.0.0")
                        .description(
                                "面向客服、产品经理、开发测试三个岗位的反馈闭环管理系统。" +
                                "核心设计：原始反馈只读、AI 产出与人工决策物理分离、" +
                                "AI 不可用时自动降级为手工模式且不中断业务。"
                        ))
                // 全局声明：所有接口默认携带 Bearer Token
                .addSecurityItem(new SecurityRequirement()
                        .addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "请先调用 /api/auth/login 获取 token，" +
                                                "然后在此处填入（无需加 Bearer 前缀）"
                                        )));
    }
}
