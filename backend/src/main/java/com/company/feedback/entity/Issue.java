package com.company.feedback.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 已确认问题实体【核心业务对象 · 正式】
 *
 * <p>对应表：issue
 *
 * <p><b>身份定位</b>：只有经过 PM 确认的问题才会写入本表。
 * 本表中的数据被视为「业务事实」，参与统计、生成任务、驱动看板。
 *
 * <p><b>本表最重要的设计：ai_draft_content 与 final_content 并存</b>
 * <pre>
 *   ai_draft_content  = AI 原话（写入后永不覆盖）
 *   final_content     = PM 定稿内容
 * </pre>
 * 只要这两个字段不一致，就证明 PM 对 AI 的建议做了修改。
 * 这是验收场景 5「AI 生成内容与人工确认结果可区分」最直接、最无法辩驳的证据。
 *
 * <p>即使 PM 原样采纳，两个字段内容相同，
 * 但 source_type=AI 且 confirmed_by 有值，同样能证明"这是 AI 起草、人确认的"。
 *
 * @author 反馈改进助手项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("issue")
public class Issue extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 问题编号，如 IS-0001
     */
    @TableField("issue_no")
    private String issueNo;

    /**
     * 问题标题
     */
    @TableField("title")
    private String title;

    /**
     * 问题描述
     */
    @TableField("description")
    private String description;

    /**
     * 功能模块
     */
    @TableField("module")
    private String module;

    /**
     * AI 生成的需求草稿（保留原话，永不覆盖）
     *
     * <p>由 PM 确认候选卡片时一并带入；
     * 若是 PM 手工创建的问题，该字段为空。
     */
    @TableField("ai_draft_content")
    private String aiDraftContent;

    /**
     * PM 定稿的需求说明
     */
    @TableField("final_content")
    private String finalContent;

    /**
     * 验收条件（PM 确认后填写）
     */
    @TableField("acceptance_criteria")
    private String acceptanceCriteria;

    /**
     * 来源类型：AI / MANUAL
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 确认人（PM）
     */
    @TableField("confirmed_by")
    private Long confirmedBy;

    /**
     * 确认时间
     */
    @TableField("confirm_time")
    private LocalDateTime confirmTime;

    /**
     * 逻辑删除：0-正常 1-已删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /**
     * 乐观锁版本号
     */
    @Version
    @TableField("version")
    private Integer version;
}
