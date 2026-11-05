package com.company.feedback.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 原始反馈实体【核心业务对象 · 只读】
 *
 * <p>对应表：feedback
 *
 * <p><b>最重要的业务约定</b>：
 * 本实体<b>不提供任何修改 content 字段的业务接口</b>。
 *
 * <p>原因（对应选题文档 AI 约束 A6）：
 * 原始反馈是后续所有 AI 归纳与人工决策的依据。
 * 一旦允许修改原文，整个决策链条就失去了可追溯性——
 * 事后无法回答"当初 AI 是看到什么才得出这个结论的"。
 *
 * <p>如需更正，正确做法是：新增一条反馈并备注说明，原记录永久保留。
 *
 * @author 反馈改进助手项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("feedback")
public class Feedback extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 反馈编号，如 FB20261026-0001
     */
    @TableField("feedback_no")
    private String feedbackNo;

    /**
     * 反馈原文（脱敏）
     *
     * <p>只读字段：系统层不提供修改入口
     */
    @TableField("content")
    private String content;

    /**
     * 功能模块：EXPENSE_FORM / INVOICE_UPLOAD / APPROVAL_PROGRESS / RETURN_MODIFY / OTHER
     */
    @TableField("module")
    private String module;

    /**
     * 来源渠道：TICKET / SURVEY / CALL
     */
    @TableField("source")
    private String source;

    /**
     * 脱敏客户编号，如 C-0017
     *
     * <p>禁止存储真实姓名、单位名称
     */
    @TableField("customer_tag")
    private String customerTag;

    /**
     * 凭证图片地址 JSON 数组
     */
    @TableField("image_urls")
    private String imageUrls;

    /**
     * 状态：NEW / LINKED / CLOSED / IGNORED
     */
    @TableField("status")
    private String status;

    /**
     * 录入人（sys_user.id）
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 逻辑删除标记：0-正常 1-已删除
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
