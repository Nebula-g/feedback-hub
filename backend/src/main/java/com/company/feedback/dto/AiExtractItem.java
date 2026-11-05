package com.company.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条反馈的结构化提取结果（AI 作用点 A：EXTRACT）
 *
 * <p><b>为什么一条反馈会提取出多个 item</b>：
 * 用户经常在一段话里同时抱怨两件事，例如
 * "上传发票总是失败，而且报销单被退回后也找不到原因"。
 * 如果强行归成一个问题，另一半诉求就会被吞掉。
 * 因此提取阶段允许"一对多"，后续的聚类再按 item 归并。
 * 这就是验收场景 2「一条反馈拆成多个问题」的实现基础。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiExtractItem {

    /**
     * 来源反馈 ID
     *
     * <p>必须保留，这是"每一条 AI 结论都能回查到原始材料"的关键字段。
     */
    private Long feedbackId;

    /**
     * 判定所属的功能模块
     *
     * <p>聚类时作为<b>硬分组依据</b>，跨模块绝不合并。
     */
    private String module;

    /**
     * 问题现象：用户实际遇到了什么
     */
    private String phenomenon;

    /**
     * 用户诉求：用户希望怎么解决
     */
    private String userDemand;

    /**
     * 现象签名
     *
     * <p>规则实现用它做分组 key；大模型实现不使用这个字段，
     * 但保留在结构里，便于两种实现共用同一套下游逻辑。
     */
    private String signature;

    /**
     * 是否通过「跨模块兜底匹配」命中
     *
     * <p>仅规则实现使用：反馈的标注模块里没有命中任何规则，
     * 却在<b>其他模块</b>的规则表里命中了关键词。
     * 这类命中可靠性明显更低（同一个词如「网络错误」在多个模块都很常见），
     * 下游聚类时会据此降低置信度并在归类理由里提示 PM 核对。
     */
    private boolean crossModule;
}
