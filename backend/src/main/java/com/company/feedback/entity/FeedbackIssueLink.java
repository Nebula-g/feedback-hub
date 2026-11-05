package com.company.feedback.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 反馈与问题关联实体【核心业务对象】
 *
 * <p>对应表：feedback_issue_link
 *
 * <p><b>为什么需要这张关联表</b>：
 * 反馈与问题是<b>多对多</b>关系，两个方向都成立：
 * <ul>
 *   <li>一条反馈可能包含多个问题 → 需要被拆分（如"发票上传失败，而且审批也看不到进度"）</li>
 *   <li>一个问题可能由多条反馈归纳 → 需要被合并（如 7 条不同表述都在说发票上传失败）</li>
 * </ul>
 * 如果把 issue_id 直接写在 feedback 表上，
 * 一条反馈就只能属于一个问题，拆分场景无法实现。
 *
 * <p><b>link_type 字段的意义</b>：
 * 区分这条关联是 AI 建议的，还是人手工建立的。
 * 对应验收场景 3 的要求——「哪些是 AI 建议的、哪些是人工确认的」必须能分清。
 *
 * <p><b>candidate_id 字段的意义</b>：
 * 记录这条关联源自哪张候选卡片，实现"关联 → 卡片 → AI 日志"的三级追溯。
 * PM 手工关联时该字段为空。
 *
 * <p>注意：本表不设置逻辑删除。
 * 解除关联应当是显式的删除动作，并写入 task_event 留痕，
 * 而不是静默的逻辑删除（那样会丢失"曾经关联过"这个事实）。
 *
 * <p><b>为什么本实体不继承 BaseEntity</b>：
 * 关联记录一旦建立就不会再修改，只存在"建立"与"解除"两种动作。
 * 表里也没有 update_time 列，继承 BaseEntity 会让更新语句带上不存在的列而报错。
 *
 * @author 反馈改进助手项目组
 */
@Data
@TableName("feedback_issue_link")
public class FeedbackIssueLink implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID（数据库自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间（插入时自动填充）
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 反馈 ID
     */
    @TableField("feedback_id")
    private Long feedbackId;

    /**
     * 问题 ID
     */
    @TableField("issue_id")
    private Long issueId;

    /**
     * 来源候选卡片 ID，手工关联时为 null
     */
    @TableField("candidate_id")
    private Long candidateId;

    /**
     * 关联类型：AI / PM
     */
    @TableField("link_type")
    private String linkType;
}
