package com.company.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 确认候选卡片弹窗的 AI 起草建议
 *
 * <p><b>解决什么问题</b>：PM 点开「确认候选问题」时，需求说明由卡片
 * 现象/诉求直接拼出，但验收条件原来是空的，PM 每次都要从零写。
 * 而"怎么算解决"正是 DRAFT 作用点已经会做的事（
 * {@code AiService#draft} 本来就同时返回 draftContent 与
 * acceptanceCriteria），只是原先只对已确认的 issue 开放。
 *
 * <p><b>为什么复用 DRAFT 而不是新增一个 AI 作用点</b>：
 * 起草建议 = 对这张候选卡片"提前起草一次"，与
 * {@code POST /ai/issues/{id}/draft} 是同一个 AI 作用点
 * {@code AiBizType.DRAFT}，共用同一份提示词。
 * 这里只是把临时构造的候选信息当作 Issue 传进去，
 * 不需要新的提示词，也不新增业务类型。
 *
 * <p><b>必须是"建议"而不是"结论"</b>：
 * 预填内容进入可编辑表单，由 PM 修改确认后才随 confirm 请求落库；
 * {@link #available} 为 false 时前端保持空字段并提示手动填写，
 * 不弹错误打断确认操作。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDraftSuggestion {

    /**
     * AI 是否可用
     *
     * <p>false 表示起草调用失败（网络/密钥问题且未启用降级），
     * 前端据此保持空字段并提示"可手动填写"，而不是弹错误打断操作。
     */
    private Boolean available;

    /** 给 PM 看的说明（建议来源 / 不可用原因） */
    private String message;

    /** 需求说明草稿（仅供参考，前端默认不覆盖本地拼好的需求说明） */
    private String draftContent;

    /** 验收条件草稿（预填进验收条件输入框，PM 可改） */
    private String acceptanceCriteria;

    /** 实际产出方：LLM / RULE */
    private String provider;

    /** 是否发生了降级（规则实现兜底） */
    private Boolean fallback;
}
