package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 反馈录入请求参数
 *
 * <p><b>设计说明</b>：本对象只包含客服录入时需要填写的字段。
 * 诸如 status（默认 NEW）、createdBy（取当前登录人）等字段
 * 由服务端自动生成，不接受前端传入——
 * 这避免了客户端伪造"已关联"状态或冒用他人身份录入的问题。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "反馈录入请求")
public class FeedbackCreateRequest {

    /** 反馈原文（脱敏） */
    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 2000, message = "反馈内容不能超过2000字")
    @Schema(description = "反馈原文（已脱敏）",
            example = "报销单提交后一直转圈，最后提示超时失败")
    private String content;

    /** 功能模块 */
    @NotNull(message = "功能模块不能为空")
    @Schema(description = "功能模块：EXPENSE_FORM / INVOICE_UPLOAD / "
            + "APPROVAL_PROGRESS / RETURN_MODIFY / OTHER",
            example = "INVOICE_UPLOAD")
    private String module;

    /** 来源渠道 */
    @NotNull(message = "来源渠道不能为空")
    @Schema(description = "来源渠道：TICKET / SURVEY / CALL", example = "TICKET")
    private String source;

    /** 脱敏客户编号 */
    @Size(max = 32, message = "客户编号不能超过32字符")
    @Schema(description = "脱敏客户编号，如 C-0017", example = "C-0017")
    private String customerTag;

    /** 凭证图片地址列表 */
    @Schema(description = "凭证图片访问地址列表")
    private List<String> imageUrls;
}
