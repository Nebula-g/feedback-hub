package com.company.feedback.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 候选问题卡片实体【核心业务对象 · 草稿】
 *
 * <p>对应表：ai_candidate_issue
 *
 * <p><b>本实体是整个项目设计的关键所在（对应 AI 约束 A1、A2、A5）</b>：
 * AI 的产出只是「建议」，在没有经过 PM 确认之前，
 * 它<b>不构成任何业务事实</b>，不参与统计，不产生任务，不影响任何看板数据。
 *
 * <p>物理分离的具体含义：
 * <pre>
 *   ai_candidate_issue（AI 草稿）  ──PM 确认──▶  issue（正式事实）
 * </pre>
 * 两张表，两种身份。PM 确认时会发生一次「数据搬运」：
 * 把候选卡片的内容复制生成一条新的 issue 记录，
 * 同时在 issue.ai_draft_content 里永久保留 AI 原话。
 *
 * <p>这样做的收益（验收场景 3、5 直接考这个）：
 * <ul>
 *   <li>可以随时证明"AI 原本建议的是 A，PM 最终定的是 B"</li>
 *   <li>驳回的候选卡片不会污染正式数据</li>
 *   <li>AI 反复重试不会产生脏数据堆积</li>
 * </ul>
 *
 * @author 反馈改进助手项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_candidate_issue")
public class AiCandidateIssue extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 生成批次号：同一次「生成候选问题」操作共享
     */
    @TableField("batch_no")
    private String batchNo;

    /**
     * 候选问题标题（AI 生成，PM 可修改后确认）
     */
    @TableField("title")
    private String title;

    /**
     * 问题现象：用户实际遇到了什么
     */
    @TableField("phenomenon")
    private String phenomenon;

    /**
     * 用户诉求：用户希望怎么解决
     */
    @TableField("user_demand")
    private String userDemand;

    /**
     * 功能模块
     *
     * <p>重要：这是聚类的<b>硬分组依据</b>。
     * 系统只在同一个 module 内部让 AI 做语义聚类，
     * 跨模块的反馈不会被合并，从根本上避免误合并。
     */
    @TableField("module")
    private String module;

    /**
     * AI 置信度 0.00-1.00
     *
     * <p>设计约束：<b>置信度只用于排序展示，不用于自动决策</b>。
     * 即便置信度为 0.99，也仍需 PM 手动点击确认。
     */
    @TableField("ai_confidence")
    private BigDecimal aiConfidence;

    /**
     * AI 给出该归类的理由
     *
     * <p>让 PM 判断"AI 为什么觉得这几条是一回事"，
     * 是人工审核能否真正落地的必要条件。
     */
    @TableField("ai_reason")
    private String aiReason;

    /**
     * 关联反馈 ID 的 JSON 数组，如 [1,2,3,7]
     */
    @TableField("related_feedback_ids")
    private String relatedFeedbackIds;

    /**
     * 状态：DRAFT / CONFIRMED / MODIFIED / MERGED / REJECTED
     *
     * <p>说明：
     * <ul>
     *   <li>DRAFT：AI 生成，等待 PM 处理</li>
     *   <li>CONFIRMED：PM 原样确认</li>
     *   <li>MODIFIED：PM 修改后确认（此状态是"人改了 AI"的直接证据）</li>
     *   <li>MERGED：PM 将其并入已有问题</li>
     *   <li>REJECTED：PM 驳回，需填写 handle_remark</li>
     * </ul>
     */
    @TableField("status")
    private String status;

    /**
     * 处理人（PM）
     */
    @TableField("handled_by")
    private Long handledBy;

    /**
     * 处理说明：驳回、合并时必填
     */
    @TableField("handle_remark")
    private String handleRemark;

    /**
     * 处理时间
     */
    @TableField("handle_time")
    private LocalDateTime handleTime;

    /**
     * 确认后生成的正式问题 ID
     *
     * <p>用途：从候选卡片反查"它最终变成了哪个正式问题"，
     * 构成完整追溯链。
     */
    @TableField("generated_issue_id")
    private Long generatedIssueId;

    /**
     * 逻辑删除：0-正常 1-已删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /**
     * 乐观锁版本号：防止 PM 并发重复确认同一张卡片
     */
    @Version
    @TableField("version")
    private Integer version;
}
