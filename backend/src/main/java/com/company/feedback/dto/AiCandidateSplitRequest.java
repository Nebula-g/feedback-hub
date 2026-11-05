package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * PM 手动拆分候选卡片请求
 *
 * <p><b>这个请求解决的是验收场景中的一类典型问题</b>：
 * 一条反馈里同时写了多件不同的事（例如"发票上传总是失败，
 * 而且报销单被退回后也看不到退回原因"），AI 把它们归纳成了
 * 一张候选卡片。PM 判定这两个问题应当分开处理时，
 * 用本请求把原卡片拆成多张新候选卡片，逐张确认、逐个生成问题。
 *
 * <p><b>关键设计：feedbackIds 允许在多个部分中重复出现</b>。
 * 因为"混在一起"的根源往往就是同一条反馈里包含多个问题——
 * 按反馈拆分根本拆不开，必须允许 PM 为每个部分改写
 * 标题 / 现象 / 诉求，同一条反馈同时挂在多张新卡片上。
 * 后续每张卡片确认时会各自建立 feedback_issue_link，
 * 反馈与问题天然形成多对多，数据结构无需任何变更。
 *
 * <p><b>完整性约束</b>：所有部分的 feedbackIds 并集必须覆盖
 * 原卡片的全部关联反馈，避免某条反馈在拆分后掉出归类流程。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "拆分候选卡片请求")
public class AiCandidateSplitRequest {

    /** 拆分后的各个部分（至少 2 个） */
    @NotNull(message = "拆分内容不能为空")
    @Valid
    @Schema(description = "拆分后的候选卡片列表")
    private List<Part> parts;

    /**
     * 拆分后的单张候选卡片
     */
    @Data
    @Schema(description = "拆分后的单张候选卡片")
    public static class Part {

        /** 新卡片标题（必填：拆分本身就是 PM 的重新归纳，必须给出明确结论） */
        @NotBlank(message = "拆分后每张卡片的标题不能为空")
        @Size(max = 200, message = "标题不能超过200字")
        @Schema(description = "新卡片标题")
        private String title;

        /** 新卡片的问题现象 */
        @Size(max = 2000, message = "现象描述不能超过2000字")
        @Schema(description = "新卡片的问题现象")
        private String phenomenon;

        /** 新卡片的用户诉求 */
        @Size(max = 2000, message = "诉求描述不能超过2000字")
        @Schema(description = "新卡片的用户诉求")
        private String userDemand;

        /**
         * 归入该部分的反馈 ID 列表
         *
         * <p>为空时继承原卡片的全部关联反馈；
         * 非空时必须是原集合的子集，且允许与其他部分重复
         * （一条反馈包含多个问题的场景）。
         */
        @Schema(description = "归入该部分的反馈ID列表，为空则继承原卡片的全部关联反馈")
        private List<Long> feedbackIds;
    }
}
