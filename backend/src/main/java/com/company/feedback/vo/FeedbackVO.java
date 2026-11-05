package com.company.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 反馈视图对象
 *
 * <p><b>为什么需要 VO 而不是直接返回实体</b>：
 * <ol>
 *   <li>实体字段与数据库列一一对应，直接返回会暴露内部实现（如 version、deleted）</li>
 *   <li>枚举代码（如 INVOICE_UPLOAD）前端展示时需要中文名（发票上传），
 *       在 VO 层补上 label 字段，避免前端维护一份映射表</li>
 *   <li>image_urls 在库中是 JSON 字符串，前端需要的是数组，转换放在 VO 层</li>
 * </ol>
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@Schema(description = "反馈信息")
public class FeedbackVO {

    /** 主键 ID */
    @Schema(description = "反馈ID", example = "1")
    private Long id;

    /** 反馈编号 */
    @Schema(description = "反馈编号", example = "FB20261026-0001")
    private String feedbackNo;

    /** 反馈原文（脱敏） */
    @Schema(description = "反馈原文（只读）",
            example = "报销单提交后一直转圈，最后提示超时失败")
    private String content;

    /** 功能模块代码 */
    @Schema(description = "功能模块代码", example = "INVOICE_UPLOAD")
    private String module;

    /** 功能模块中文名 */
    @Schema(description = "功能模块中文名", example = "发票上传")
    private String moduleLabel;

    /** 来源渠道代码 */
    @Schema(description = "来源渠道代码", example = "TICKET")
    private String source;

    /** 来源渠道中文名 */
    @Schema(description = "来源渠道中文名", example = "客服工单")
    private String sourceLabel;

    /** 脱敏客户编号 */
    @Schema(description = "脱敏客户编号", example = "C-0017")
    private String customerTag;

    /** 凭证图片地址列表 */
    @Schema(description = "凭证图片地址列表")
    private List<String> imageUrls;

    /** 状态代码 */
    @Schema(description = "状态代码", example = "NEW")
    private String status;

    /** 状态中文名 */
    @Schema(description = "状态中文名", example = "未归类")
    private String statusLabel;

    /** 录入人 ID */
    @Schema(description = "录入人ID", example = "1")
    private Long createdBy;

    /** 录入人姓名 */
    @Schema(description = "录入人姓名", example = "张客服")
    private String createdByName;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
