package com.company.feedback.service;

import com.company.feedback.dto.AiClusterResult;
import com.company.feedback.dto.AiDraftResult;
import com.company.feedback.dto.AiExtractItem;
import com.company.feedback.entity.Feedback;
import com.company.feedback.entity.Issue;

import java.util.List;

/**
 * AI 能力服务接口
 *
 * <p><b>本接口定义了 AI 在本系统中的三个，且仅有的三个作用点</b>：
 * <ol>
 *   <li>{@link #extract} —— 从单条反馈中提取结构化的问题现象与用户诉求</li>
 *   <li>{@link #cluster} —— 把一批反馈归纳成候选问题卡片</li>
 *   <li>{@link #draft}   —— 为已确认问题起草需求说明与验收条件</li>
 * </ol>
 * 除此之外 AI 不做任何事，尤其<b>不做优先级判断</b>。
 *
 * <p><b>为什么必须先定义接口再写实现</b>：
 * 本项目要求「AI 是加速器，不是必经路」。
 * 只有当调用方依赖的是接口而不是具体的大模型 SDK 时，
 * 才能在网络不通、Key 失效的情况下无缝切换到 {@code RuleAiServiceImpl}。
 * 如果调用方直接依赖 HTTP 客户端，降级就无从谈起。
 *
 * <p><b>两种实现</b>：
 * <ul>
 *   <li>{@code LlmAiServiceImpl} —— 真实大模型，效果好，依赖网络与密钥</li>
 *   <li>{@code RuleAiServiceImpl} —— 关键词 + 模板，效果一般，零依赖、断网可用</li>
 * </ul>
 *
 * @author 反馈改进助手项目组
 */
public interface AiService {

    /**
     * 实现类型代码
     *
     * <p>取值为 {@code AiProvider} 的 code：LLM / RULE。
     * 该值会写入 ai_generation_log.provider，用于事后区分产出方。
     *
     * @return 实现类型代码
     */
    String provider();

    /**
     * 模型名称
     *
     * <p>规则实现固定返回 "rule-engine-v1"，不用 null，
     * 便于统一做统计查询（否则统计时要写 provider IS NOT NULL 之类的补丁条件）。
     *
     * @return 模型名称
     */
    String model();

    /**
     * 判断当前实现是否可用
     *
     * <p>大模型实现会在此检查配置完整性（是否有合法的 base-url 与 api-key）；
     * 规则实现恒返回 true。
     *
     * <p><b>为什么需要这个方法</b>：
     * 让上游能在"发起网络请求之前"就做出降级决策，
     * 而不是等 30 秒超时后再降级。
     *
     * @return 可用返回 true
     */
    boolean available();

    /**
     * 作用点 A · 单条提取
     *
     * <p>把一段自然语言反馈拆成结构化的现象与诉求。
     *
     * <p><b>一条反馈可能返回多个 item</b>：
     * 当反馈中出现"而且 / 并且 / 另外 / 同时"等并列连词时，
     * 说明用户在说两件事，需要拆开，否则另一半诉求会被吞掉。
     *
     * @param feedback 原始反馈（只读，本方法不得修改它）
     * @return 提取结果列表，至少包含 1 个元素
     */
    List<AiExtractItem> extract(Feedback feedback);

    /**
     * 作用点 B · 聚类成候选卡片
     *
     * <p><b>硬约束：禁止跨模块合并</b>。
     * 实现必须保证同一张卡片里的所有反馈属于同一个功能模块，
     * 哪怕它们的文字表述高度相似。
     *
     * @param feedbacks 待归纳的反馈列表（调用方已按模块筛选）
     * @return 聚类结果（含 AI 返回原文，供落库留证）
     */
    AiClusterResult cluster(List<Feedback> feedbacks);

    /**
     * 作用点 C · 起草需求
     *
     * <p>为已确认问题生成需求说明与验收条件草稿。
     * 返回的草稿<b>不会</b>被直接写入 issue.final_content，
     * 必须经 PM 编辑确认。
     *
     * @param issue             问题
     * @param feedbackContents  该问题关联反馈的原文列表（作为素材）
     * @return 草稿结果
     */
    AiDraftResult draft(Issue issue, List<String> feedbackContents);
}
