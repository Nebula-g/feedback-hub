package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PM 合并候选卡片请求
 *
 * <p><b>合并的含义</b>：PM 判定这张候选卡片描述的问题<b>已经存在</b>
 * （例如历史上已确认过同一问题），不需要再单独建单——
 * 把卡片的关联反馈挂到目标问题上，原卡片转为 MERGED 留档，
 * AI 原稿仍然可追溯。
 *
 * <p><b>与拆分的互补关系</b>：拆分处理"一张卡片混了多个问题"，
 * 合并处理"多个卡片/已有问题其实是一件事"。两者加上确认与驳回，
 * 构成 PM 对 AI 归纳结果的完整干预手段。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "合并候选卡片请求")
public class AiCandidateMergeRequest {

    /** 并入的目标问题 ID */
    @NotNull(message = "目标问题不能为空")
    @Schema(description = "并入的目标问题ID", example = "8")
    private Long targetIssueId;

    /** 合并说明（可选，会记入卡片的处理备注） */
    @Size(max = 500, message = "合并说明不能超过500字")
    @Schema(description = "合并说明，如：与 IS-0008 是同一问题，反馈已并入该单")
    private String remark;
}
