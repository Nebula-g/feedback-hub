package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 反馈列表查询参数
 *
 * <p>所有筛选条件均为可选，不传即为查询全部。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "反馈列表查询参数")
public class FeedbackQueryRequest {

    /** 页码，默认 1 */
    @Schema(description = "页码，从1开始", example = "1")
    private Long pageNo = 1L;

    /** 每页条数，默认 10 */
    @Schema(description = "每页条数", example = "10")
    private Long pageSize = 10L;

    /** 按功能模块筛选 */
    @Schema(description = "功能模块筛选", example = "INVOICE_UPLOAD")
    private String module;

    /** 按状态筛选 */
    @Schema(description = "状态筛选：NEW / LINKED / CLOSED / IGNORED",
            example = "NEW")
    private String status;

    /** 按来源渠道筛选 */
    @Schema(description = "来源渠道筛选：TICKET / SURVEY / CALL")
    private String source;

    /** 关键字模糊搜索（匹配反馈原文与编号） */
    @Schema(description = "关键字，模糊匹配反馈原文与编号", example = "发票")
    private String keyword;
}
