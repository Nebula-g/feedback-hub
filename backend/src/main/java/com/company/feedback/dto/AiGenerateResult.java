package com.company.feedback.dto;

import com.company.feedback.vo.AiCandidateVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 生成候选问题的返回结果
 *
 * <p><b>provider 与 fallback 两个字段是给前端做"诚实提示"用的</b>：
 * 当 fallback=true 时，前端应当明确告知使用者
 * 「本次结果由本地规则生成，非大模型产出」，而不是假装一切正常。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@Schema(description = "生成候选问题结果")
public class AiGenerateResult {

    /** 批次号 */
    @Schema(description = "批次号", example = "B20261027153000")
    private String batchNo;

    /** 生成的卡片数量 */
    @Schema(description = "卡片数量", example = "5")
    private int count;

    /** 本次实际使用的实现类型：LLM / RULE */
    @Schema(description = "实现类型", example = "LLM")
    private String provider;

    /** 是否发生了降级（原本期望用大模型，实际用了规则） */
    @Schema(description = "是否降级")
    private boolean fallback;

    /** 提示信息 */
    @Schema(description = "提示信息")
    private String message;

    /** 生成的候选卡片 */
    private List<AiCandidateVO> candidates;
}
