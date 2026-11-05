package com.company.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 建议的候选问题卡片（AI 作用点 B：CLUSTER 的单条产出）
 *
 * <p><b>字段设计的红线：这里没有 priority 字段</b>。
 * 课程要求 AI 不得自动决定优先级，
 * AI 只提供客观依据（关联了几条反馈、涉及几个客户、属于哪个模块），
 * 最终定级由 PM 在「问题转任务」时人工完成。
 *
 * <p>confidence 同样只是排序参考，不触发任何自动动作：
 * 哪怕 0.99 也仍需 PM 点击确认。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCandidateSuggestion {

    /** 候选问题标题 */
    private String title;

    /** 问题现象（归纳后的共同现象） */
    private String phenomenon;

    /** 用户诉求（归纳后的共同诉求） */
    private String userDemand;

    /** 功能模块 */
    private String module;

    /** 置信度 0.00-1.00，仅用于排序展示 */
    private BigDecimal confidence;

    /** AI 给出该归类的理由，供 PM 判断"为什么算同一件事" */
    private String reason;

    /** 该卡片所依据的反馈 ID 列表 */
    private List<Long> feedbackIds;
}
