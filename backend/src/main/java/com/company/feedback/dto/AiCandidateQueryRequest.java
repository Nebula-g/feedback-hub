package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 候选卡片分页查询条件
 *
 * <p>沿用全局分页约定：pageNo 从 1 开始，pageSize 默认 10。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "AI候选卡片查询条件")
public class AiCandidateQueryRequest {

    /** 功能模块 */
    @Schema(description = "功能模块", example = "INVOICE_UPLOAD")
    private String module;

    /**
     * 卡片状态
     *
     * <p>可取值：DRAFT / CONFIRMED / MODIFIED / MERGED / REJECTED
     */
    @Schema(description = "状态", example = "DRAFT")
    private String status;

    /** 页码，从 1 开始 */
    @Schema(description = "页码", example = "1")
    private Long pageNo = 1L;

    /** 每页条数 */
    @Schema(description = "每页条数", example = "10")
    private Long pageSize = 10L;
}
