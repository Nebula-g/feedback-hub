package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建改进任务请求参数
 *
 * <p><b>业务规则（对应需求文档第 4 节）</b>：
 * <ul>
 *   <li>任务必须由已确认的 issue 转化而来，不能凭空创建（R3 链条完整性）</li>
 *   <li>优先级由 PM 手动设定，且必须填写理由（R1）</li>
 *   <li>负责人可以留空，留空表示暂不指派，后续由 PM 补指派</li>
 * </ul>
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "创建改进任务请求")
public class TaskCreateRequest {

    /** 来源问题 ID */
    @NotNull(message = "来源问题不能为空")
    @Schema(description = "来源问题 ID", example = "1")
    private Long issueId;

    /** 任务标题 */
    @NotBlank(message = "任务标题不能为空")
    @Schema(description = "任务标题", example = "修复发票上传大文件超时问题")
    private String title;

    /** 需求说明 */
    @Schema(description = "需求说明",
            example = "支持 10MB 以上发票图片的分片上传，并在失败时给出明确提示")
    private String requirement;

    /** 验收条件 */
    @Schema(description = "验收条件",
            example = "20MB 以内的 PDF 与 JPG 均可上传成功，失败时有明确错误提示")
    private String acceptanceCriteria;

    /**
     * 优先级 P0 / P1 / P2
     *
     * <p><b>本字段必须由 PM 手动传入，AI 无写入权限</b>。
     */
    @NotBlank(message = "优先级不能为空")
    @Schema(description = "优先级：P0 / P1 / P2", example = "P0")
    private String priority;

    /**
     * 定级理由（必填）
     *
     * <p>强制填写是为了让每一次定级都有据可查，
     * 避免出现"拍脑袋定 P0"却讲不出理由的情况。
     */
    @NotBlank(message = "定级理由不能为空")
    @Schema(description = "定级理由",
            example = "7 条反馈集中在该问题，涉及 5 家客户，且阻塞报销提交主流程")
    private String priorityReason;

    /** 负责人 ID，可为空表示暂不指派 */
    @Schema(description = "负责人 ID（DEV），可为空", example = "3")
    private Long assigneeId;
}
