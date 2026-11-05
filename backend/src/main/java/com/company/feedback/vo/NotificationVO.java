package com.company.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内通知视图对象
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "站内通知")
public class NotificationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "通知 ID")
    private Long id;

    @Schema(description = "通知类型代码", example = "TASK_ASSIGNED")
    private String type;

    @Schema(description = "通知标题")
    private String title;

    @Schema(description = "通知正文")
    private String content;

    @Schema(description = "点击跳转的前端路由")
    private String link;

    @Schema(description = "是否已读")
    private Boolean isRead;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
