package com.company.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 拆分候选卡片的 AI 预填建议
 *
 * <p><b>解决什么问题</b>：PM 点开「拆分」时，原先只能拿到一个空模板，
 * 需要自己把标题、现象、诉求全部重打一遍。而"这张卡片混了几个问题"
 * 恰恰是 AI 最擅长判断的事（它本来就做过一次归纳，只是归错了粒度）。
 *
 * <p><b>为什么复用 CLUSTER 而不是新增一个 AI 作用点</b>：
 * 拆分建议 = 对这张卡片关联的反馈"重新聚类一次"，与生成候选卡片
 * 是同一个 AI 作用点 {@code AiBizType.CLUSTER}，只是入口不同。
 * 因此这里直接调用 {@code AiService#cluster}，不需要新的提示词，
 * 也不需要给 {@code AiBizType} 增加第四种业务类型——
 * 那会破坏"AI 只有三个作用点"的设计约束。
 *
 * <p><b>必须是"建议"而不是"结论"</b>：
 * 预填内容全部由 PM 二次确认后才生效，{@link #splitSuggested}
 * 为 false 时前端仍然允许手动拆分。AI 在这里只提供参考，不做决策。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCandidateSplitSuggestion {

    /**
     * AI 是否可用
     *
     * <p>false 表示聚类调用失败（网络/密钥问题且未启用降级），
     * 前端据此保持空模板并提示"可手动拆分"，而不是弹错误打断操作。
     */
    private Boolean available;

    /**
     * AI 是否认为这张卡片应当拆分
     *
     * <p>true 表示识别出 2 个及以上独立问题；false 表示 AI 认为
     * 这些反馈描述的是同一件事（此时 parts 仍会带回原稿，供 PM 手动补全）。
     */
    private Boolean splitSuggested;

    /** 给 PM 看的说明（AI 判断依据 / 不可用原因） */
    private String message;

    /** 实际产出方：LLM / RULE */
    private String provider;

    /** 是否发生了降级（规则实现兜底） */
    private Boolean fallback;

    /** 预填的拆分部分（至少 1 个，标题/现象/诉求可直接编辑） */
    private List<AiCandidateSplitRequest.Part> parts;
}
