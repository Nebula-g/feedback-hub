package com.company.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聚类结果封装
 *
 * <p>除了候选卡片列表，还保留模型的<b>返回原文</b>。
 *
 * <p>为什么要多带一个 rawOutput：
 * 如果只保存解析后的结构化结果，那么"AI 当时到底说了什么"就再也找不回来了。
 * 一旦有人质疑结论被人为加工过，就没有证据自证。
 * 因此这里把原文一并带回，由调用方原样写入
 * ai_generation_log.output_raw，构成不可篡改的证据。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClusterResult {

    /** 解析后的候选卡片列表 */
    private List<AiCandidateSuggestion> candidates;

    /**
     * AI 返回原文
     *
     * <p>规则实现时为按模板生成的文本，同样原样保存，
     * 便于事后判断"这条是规则兜底出来的"。
     */
    private String rawOutput;
}
