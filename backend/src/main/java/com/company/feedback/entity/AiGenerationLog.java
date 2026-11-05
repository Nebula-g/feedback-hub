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
 * AI 调用日志实体【核心业务对象 · 证据链】
 *
 * <p>对应表：ai_generation_log
 *
 * <p><b>为什么必须有这张表（对应选题文档 AI 约束 A3、A7）</b>：
 * 本系统区分「AI 说了什么」和「人最终决定了什么」。
 * 如果没有全量日志，事后无法回答三个关键问题：
 * <ol>
 *   <li>AI 当时看到了哪些反馈原文？（input_snapshot）</li>
 *   <li>AI 原话是怎么说的，有没有被人为篡改或美化？（output_raw）</li>
 *   <li>这次调用是成功、失败还是超时？失败后业务是否继续？（status + error_msg）</li>
 * </ol>
 *
 * <p>因此本表的写入策略是：<b>无论成功失败，一律落库</b>。
 * 失败日志同样是证据——它证明系统确实尝试过，并且确实降级了。
 *
 * <p>注意：本表<b>不设置逻辑删除字段</b>。
 * 日志一旦产生就是既成事实，删除它会破坏证据链的完整性。
 * 这一点与业务表（feedback / issue / task）的策略相反，是刻意区分的。
 *
 * <p><b>为什么本实体不继承 BaseEntity</b>：
 * ai_generation_log 是<b>追加写（append-only）</b>表，
 * 建表时只定义了 id 与 create_time，没有 update_time 列。
 * 如果继承 BaseEntity，MyBatis-Plus 生成的 UPDATE 语句
 * 会带上不存在的 update_time 列，直接抛 Unknown column 异常。
 * 这一点与 {@link FeedbackIssueLink}、{@link TaskEvent} 的处理一致。
 *
 * @author 反馈改进助手项目组
 */
@Data
@TableName("ai_generation_log")
public class AiGenerationLog implements Serializable {

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
     * 业务类型：EXTRACT / CLUSTER / DRAFT
     *
     * <p>三种类型对应 AI 在本系统中的三个作用点：
     * <ul>
     *   <li>EXTRACT：从单条反馈中提取现象与诉求</li>
     *   <li>CLUSTER：把一批同类反馈聚合成候选问题卡片</li>
     *   <li>DRAFT：为已确认问题起草需求说明</li>
     * </ul>
     */
    @TableField("biz_type")
    private String bizType;

    /**
     * 关联业务记录 ID
     *
     * <p>EXTRACT 时为 feedback.id，CLUSTER 时为 null（用 batch_no），
     * DRAFT 时为 issue.id。
     */
    @TableField("biz_id")
    private Long bizId;

    /**
     * 批次号：同一次批量生成共享一个批次号
     *
     * <p>用途：PM 在工作台上按批次查看"这一批 AI 给出了什么"，
     * 也是 ai_candidate_issue.batch_no 的关联字段。
     */
    @TableField("batch_no")
    private String batchNo;

    /**
     * 实现类型：LLM / RULE
     *
     * <p>记录本次到底是真实大模型产出，还是本地规则兜底产出。
     * 这是「AI 降级」最直接的证据。
     */
    @TableField("provider")
    private String provider;

    /**
     * 模型名称，如 deepseek-chat
     *
     * <p>RULE 实现时该字段写 "rule-engine-v1"，不用 null，
     * 方便统一查询统计。
     */
    @TableField("model")
    private String model;

    /**
     * 输入快照：本次调用喂给 AI 的完整原始内容（JSON 字符串）
     *
     * <p>必须包含所用反馈的 id 与 content 原文，
     * 否则事后无法还原 AI 的判断依据。
     */
    @TableField("input_snapshot")
    private String inputSnapshot;

    /**
     * AI 输出原文：未经任何加工，原样保存
     *
     * <p>即便解析失败也要保存，因为"AI 说了一句无法解析的胡话"
     * 本身就是需要被记录和分析的事实。
     */
    @TableField("output_raw")
    private String outputRaw;

    /**
     * 调用状态：SUCCESS / FAILED / TIMEOUT / FALLBACK
     *
     * <p>FALLBACK 是本项目要重点展示的状态：
     * 它意味着"大模型没调通，但结果由规则补齐了，业务没断"。
     */
    @TableField("status")
    private String status;

    /**
     * 失败原因（成功时为 null）
     */
    @TableField("error_msg")
    private String errorMsg;

    /**
     * 耗时（毫秒）
     *
     * <p>用于超时分析和演示时说明"为什么需要设置超时阈值"。
     */
    @TableField("cost_ms")
    private Integer costMs;

    /**
     * 操作人（sys_user.id）
     */
    @TableField("operator_id")
    private Long operatorId;
}
