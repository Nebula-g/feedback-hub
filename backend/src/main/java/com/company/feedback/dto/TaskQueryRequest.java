package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务列表查询参数
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "任务列表查询参数")
public class TaskQueryRequest {

    /** 页码，默认 1 */
    @Schema(description = "页码，从1开始", example = "1")
    private Long pageNo = 1L;

    /** 每页条数，默认 10 */
    @Schema(description = "每页条数", example = "10")
    private Long pageSize = 10L;

    /** 按状态筛选 */
    @Schema(description = "状态筛选", example = "TODO")
    private String status;

    /** 按优先级筛选 */
    @Schema(description = "优先级筛选", example = "P0")
    private String priority;

    /** 按功能模块筛选 */
    @Schema(description = "功能模块筛选", example = "INVOICE_UPLOAD")
    private String module;

    /**
     * 按负责人筛选
     *
     * <p>传 myself 表示只看分配给当前登录用户的任务，
     * 这是 DEV 工作台的默认视图。
     */
    @Schema(description = "负责人ID，传 myself 表示只看自己的", example = "myself")
    private String assigneeId;

    /** 关键字模糊搜索（任务编号、标题、需求说明） */
    @Schema(description = "关键字，模糊匹配编号、标题、需求说明")
    private String keyword;
}
