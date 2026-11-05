package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求参数
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "登录请求")
public class LoginRequest {

    /** 登录账号 */
    @NotBlank(message = "登录账号不能为空")
    @Schema(description = "登录账号", example = "pm01")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "pm123456")
    private String password;
}
