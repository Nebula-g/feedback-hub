package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 反馈批量导入请求参数
 *
 * <p><b>为什么限制单次最多 200 条</b>：
 * 批量导入是一次性写入多条记录，如果不限制条数，
 * 一个超大批次会长时间占用数据库连接并撑大事务，
 * 影响其他用户。200 条是综合考虑实际使用场景后的折中值。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "反馈批量导入请求")
public class FeedbackBatchImportRequest {

    /** 待导入的反馈列表 */
    @NotEmpty(message = "导入列表不能为空")
    @Size(max = 200, message = "单次最多导入200条")
    @Valid
    @Schema(description = "反馈列表")
    private List<FeedbackCreateRequest> items;
}
