package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 问题列表查询参数
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "问题列表查询参数")
public class IssueQueryRequest {

    /** 页码，默认 1 */
    @Schema(description = "页码，从1开始", example = "1")
    private Long pageNo = 1L;

    /** 每页条数，默认 10 */
    @Schema(description = "每页条数", example = "10")
    private Long pageSize = 10L;

    /** 按功能模块筛选 */
    @Schema(description = "功能模块筛选", example = "INVOICE_UPLOAD")
    private String module;

    /** 按来源筛选：AI / MANUAL */
    @Schema(description = "来源筛选：AI / MANUAL")
    private String sourceType;

    /** 关键字模糊搜索（标题、描述、编号） */
    @Schema(description = "关键字，模糊匹配标题、描述、编号", example = "发票")
    private String keyword;
}
