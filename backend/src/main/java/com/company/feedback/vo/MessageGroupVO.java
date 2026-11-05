package com.company.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 未读评论聚合视图对象
 *
 * <p>一个业务对象（反馈/任务）一组：标题取自对象本身，
 * 摘要取该对象下最新一条评论。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "未读评论聚合")
public class MessageGroupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "业务对象类型：FEEDBACK/TASK")
    private String bizType;

    @Schema(description = "业务对象 ID")
    private Long bizId;

    @Schema(description = "业务对象标题（反馈为内容截断，任务为任务标题）")
    private String title;

    @Schema(description = "最新一条评论内容")
    private String lastContent;

    @Schema(description = "未读评论条数")
    private Long count;

    @Schema(description = "最新评论时间")
    private LocalDateTime lastTime;
}
