package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 发表评论请求
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "发表评论请求")
public class MessageSendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "业务对象类型不能为空")
    @Schema(description = "业务对象类型：FEEDBACK/TASK")
    private String bizType;

    @NotNull(message = "业务对象ID不能为空")
    @Schema(description = "业务对象 ID")
    private Long bizId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容不能超过 1000 字")
    @Schema(description = "评论内容")
    private String content;
}
