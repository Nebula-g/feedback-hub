package com.company.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 用户选项视图对象
 *
 * <p>用于下拉框等场景。<b>刻意不含密码、盐值等任何敏感字段</b>，
 * 即便数据库里是 BCrypt 密文，也不应出现在任何接口的返回值中。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@Schema(description = "用户选项")
public class UserOptionVO {

    /** 用户 ID */
    @Schema(description = "用户ID", example = "3")
    private Long id;

    /** 显示名称 */
    @Schema(description = "显示名称", example = "王开发")
    private String displayName;

    /** 登录账号 */
    @Schema(description = "登录账号", example = "dev01")
    private String username;

    /** 所属部门 */
    @Schema(description = "所属部门", example = "研发部")
    private String dept;
}
