package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 反馈关联请求参数
 *
 * <p>用途：PM 把一条或多条反馈手工归入某个已确认问题。
 *
 * <p><b>为什么允许多对多</b>：
 * 一条反馈可能同时涉及多个问题（"上传失败而且审批也没反应"），
 * 一个问题也会由多条反馈归纳而来。因此关联关系必须独立成表，
 * 不能把 issue_id 直接写在 feedback 上。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "反馈关联请求")
public class LinkFeedbackRequest {

    /** 待关联的反馈 ID 列表 */
    @NotEmpty(message = "反馈ID列表不能为空")
    @Size(max = 200, message = "单次最多关联200条反馈")
    @Schema(description = "反馈ID列表", example = "[1, 2, 3]")
    private List<Long> feedbackIds;

    /** 关联说明（可选） */
    @Schema(description = "关联说明")
    private String remark;
}
