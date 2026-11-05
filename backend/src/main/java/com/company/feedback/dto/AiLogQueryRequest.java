package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 调用日志分页查询条件
 *
 * <p>本页数据用于「可回查」验证：
 * 可以查到每次调用的输入材料、返回原文、耗时与结果。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "AI调用日志查询条件")
public class AiLogQueryRequest {

    /**
     * 业务类型
     *
     * <p>可取值：EXTRACT / CLUSTER / DRAFT
     */
    @Schema(description = "业务类型", example = "CLUSTER")
    private String bizType;

    /**
     * 调用结果
     *
     * <p>可取值：SUCCESS / FAILED / TIMEOUT / FALLBACK
     */
    @Schema(description = "调用结果", example = "SUCCESS")
    private String status;

    /** 页码，从 1 开始 */
    @Schema(description = "页码", example = "1")
    private Long pageNo = 1L;

    /** 每页条数 */
    @Schema(description = "每页条数", example = "10")
    private Long pageSize = 10L;
}
