package com.company.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务对象评论视图对象
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "业务对象评论")
public class MessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "评论 ID")
    private Long id;

    @Schema(description = "发言人 ID")
    private Long senderId;

    @Schema(description = "发言人姓名")
    private String senderName;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "是否为当前用户本人发言")
    private Boolean mine;

    @Schema(description = "发言时间")
    private LocalDateTime createTime;
}
