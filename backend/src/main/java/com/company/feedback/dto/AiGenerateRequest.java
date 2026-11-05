package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 生成候选问题卡片的请求参数
 *
 * <p><b>module 允许为空</b>：
 * 为空时处理所有模块中尚未归类的反馈；
 * 指定时只处理该模块的反馈。
 *
 * <p>但无论 module 是否指定，<b>跨模块合并都是被禁止的</b>：
 * 聚类始终在"模块"这个硬分组内部进行。
 * 一条反馈如果被拆出两个属于不同模块的问题，
 * 会分别归入各自模块的卡片，而不是合成一张。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "生成候选问题请求")
public class AiGenerateRequest {

    /**
     * 功能模块（可空）
     *
     * <p>可取值见 {@code FeedbackModule}：
     * EXPENSE_FORM / INVOICE_UPLOAD / APPROVAL_PROGRESS / RETURN_MODIFY / OTHER
     */
    @Schema(description = "功能模块，为空表示处理所有模块",
            example = "INVOICE_UPLOAD")
    private String module;
}
