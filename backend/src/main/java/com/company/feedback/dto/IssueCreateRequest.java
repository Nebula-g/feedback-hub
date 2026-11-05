package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 问题创建请求参数（PM 手工创建）
 *
 * <p><b>与 AI 候选卡片确认的区别</b>：
 * 手工创建时 ai_draft_content 为空、source_type=MANUAL；
 * 而由候选卡片确认生成的问题，ai_draft_content 会保留 AI 原话、source_type=AI。
 * 这个区分是「AI 说了什么 / 人决定了什么」可追溯的基础。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "问题创建请求")
public class IssueCreateRequest {

    /** 问题标题 */
    @NotBlank(message = "问题标题不能为空")
    @Size(max = 200, message = "问题标题不能超过200字")
    @Schema(description = "问题标题", example = "发票上传大文件时必然失败")
    private String title;

    /** 问题描述 */
    @Size(max = 5000, message = "问题描述不能超过5000字")
    @Schema(description = "问题描述",
            example = "多位客户反馈上传超过5MB的发票PDF时上传失败")
    private String description;

    /** 功能模块 */
    @NotNull(message = "功能模块不能为空")
    @Schema(description = "功能模块", example = "INVOICE_UPLOAD")
    private String module;

    /** PM 定稿的需求说明 */
    @Schema(description = "需求说明（PM定稿）")
    private String finalContent;

    /** 验收条件 */
    @Schema(description = "验收条件", example = "5MB以上的PDF发票可在10秒内上传成功")
    private String acceptanceCriteria;
}
