package com.company.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用日志视图对象【证据链】
 *
 * <p><b>本 VO 的每一个字段都是为了回答一个具体的质疑</b>：
 * <pre>
 *   质疑                         →  用哪个字段回答
 *   "这条结论是 AI 编的吧？"      →  inputSnapshot（当时喂了什么）
 *   "有没有人美化过 AI 的原话？"  →  outputRaw（原汁原味的返回）
 *   "到底用的是大模型还是凑数的？"→  provider + model
 *   "失败了是不是就没人管了？"    →  status + errorMsg
 * </pre>
 *
 * <p>失败的调用<b>同样返回</b>，而且会带上失败原因——
 * 这证明系统确实尝试过、确实失败了、也确实降级了。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@Schema(description = "AI调用日志")
public class AiLogVO {

    /** 日志 ID */
    @Schema(description = "日志ID", example = "1")
    private Long id;

    /** 业务类型代码 */
    @Schema(description = "业务类型", example = "CLUSTER")
    private String bizType;

    /** 业务类型中文名 */
    @Schema(description = "业务类型中文名", example = "聚类成卡片")
    private String bizTypeLabel;

    /** 关联业务记录 ID */
    @Schema(description = "关联业务ID")
    private Long bizId;

    /** 批次号 */
    @Schema(description = "批次号", example = "B20261027153000")
    private String batchNo;

    /** 实现类型：LLM / RULE */
    @Schema(description = "实现类型", example = "LLM")
    private String provider;

    /** 实现类型中文名 */
    @Schema(description = "实现类型中文名", example = "大模型")
    private String providerLabel;

    /** 模型名称 */
    @Schema(description = "模型名称", example = "deepseek-chat")
    private String model;

    /** 输入材料快照（本次喂给 AI 的完整内容） */
    @Schema(description = "输入材料快照")
    private String inputSnapshot;

    /** AI 返回原文（未加工） */
    @Schema(description = "AI返回原文")
    private String outputRaw;

    /** 调用结果代码 */
    @Schema(description = "调用结果", example = "SUCCESS")
    private String status;

    /** 调用结果中文名 */
    @Schema(description = "调用结果中文名", example = "成功")
    private String statusLabel;

    /** 失败原因（成功时为空） */
    @Schema(description = "失败原因")
    private String errorMsg;

    /** 耗时（毫秒） */
    @Schema(description = "耗时毫秒", example = "4820")
    private Integer costMs;

    /** 操作人姓名 */
    @Schema(description = "操作人姓名", example = "张PM")
    private String operatorName;

    /** 操作人角色 */
    @Schema(description = "操作人角色", example = "PM")
    private String operatorRole;

    /** 链路追踪 ID */
    @Schema(description = "链路追踪ID")
    private String traceId;

    /** 调用时间 */
    @Schema(description = "调用时间")
    private LocalDateTime createTime;
}
