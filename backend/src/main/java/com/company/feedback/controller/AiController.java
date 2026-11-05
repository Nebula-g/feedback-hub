package com.company.feedback.controller;

import com.company.feedback.common.PageResult;
import com.company.feedback.common.R;
import com.company.feedback.dto.*;
import com.company.feedback.service.AiCandidateService;
import com.company.feedback.vo.AiCandidateVO;
import com.company.feedback.vo.AiLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 能力控制器
 *
 * <p><b>权限矩阵</b>：
 * <pre>
 *   接口                    CS    PM    DEV   ADMIN
 *   生成候选卡片            ✗     ✓     ✗     ✗
 *   查询候选卡片            ✗     ✓     ✗     ✗
 *   确认/驳回候选卡片       ✗     ✓     ✗     ✗
 *   拆分/合并候选卡片       ✗     ✓     ✗     ✗
 *   获取拆分预填建议        ✗     ✓     ✗     ✗
 *   起草需求草稿            ✗     ✓     ✗     ✗
 *   查看 AI 调用记录        ✗     ✓     ✗     ✓
 *   查看 AI 运行状态        ✗     ✗     ✗     ✓
 * </pre>
 *
 * <p><b>为什么生成与确认都限定 PM</b>：
 * 这两步本质上是在做"这算不算一个问题、这个问题怎么描述"的产品决策。
 * 客服只有素材没有决策权，开发只负责执行。
 * 把决策入口收窄到 PM，才能在数据层保证 issue 表里的每一条都有明确的责权人。
 *
 * <p><b>为什么查询也限定 PM/ADMIN</b>：
 * 候选卡片是"待产品决策"的工作队列，内容属于产品侧视图；
 * 与前端路由（/ai/candidates 仅 PM/ADMIN 可见）保持同一口径，
 * 避免出现"前端看不到、接口却能调"的越权缝隙。
 *
 * <p><b>关于 503</b>：生成接口在 AI 不可用且未启用降级时会返回 503。
 * 前端对 503 的处理是"提示可手工继续"，而不是报错中断——
 * 这正是"AI 是加速器不是必经路"这一设计约束的接口层体现。
 *
 * @author 反馈改进助手项目组
 */
@Tag(name = "AI 能力", description = "候选问题生成、人工确认与 AI 调用记录回查")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiCandidateService aiCandidateService;

    /**
     * 生成候选问题卡片
     *
     * <p>按模块对待归类反馈做聚类，产物全部落在草稿表，等待 PM 确认。
     *
     * @param request 生成参数（module 可空，为空处理所有模块）
     * @return 生成结果（含批次号、卡片列表、是否降级）
     */
    @Operation(summary = "生成候选问题卡片",
            description = "AI 归纳待归类反馈，产出草稿卡片；AI 不可用时按配置自动降级")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @PostMapping("/candidates/generate")
    public R<AiGenerateResult> generate(
            @Valid @RequestBody(required = false) AiGenerateRequest request) {
        return R.ok(aiCandidateService.generate(request));
    }

    /**
     * 分页查询候选卡片
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "查询候选卡片", description = "支持按模块与状态筛选")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @GetMapping("/candidates")
    public R<PageResult<AiCandidateVO>> page(@Valid AiCandidateQueryRequest query) {
        return R.ok(aiCandidateService.page(query));
    }

    /**
     * PM 确认候选卡片并生成正式问题
     *
     * <p>AI 原稿会被原样留存到 issue.ai_draft_content，PM 定稿写入 final_content。
     *
     * @param id      候选卡片 ID
     * @param request 确认内容
     * @return 生成的正式问题 ID
     */
    @Operation(summary = "确认候选卡片",
            description = "生成正式问题；若内容与 AI 建议不同则记为「修改后确认」")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @PostMapping("/candidates/{id}/confirm")
    public R<Map<String, Long>> confirm(
            @PathVariable Long id,
            @Valid @RequestBody AiCandidateConfirmRequest request) {
        return R.ok(aiCandidateService.confirm(id, request));
    }

    /**
     * PM 驳回候选卡片
     *
     * @param id      候选卡片 ID
     * @param request 驳回原因（必填）
     * @return 操作结果
     */
    @Operation(summary = "驳回候选卡片",
            description = "驳回后关联反馈回到未归类状态，不会丢失")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @PostMapping("/candidates/{id}/reject")
    public R<Void> reject(
            @PathVariable Long id,
            @Valid @RequestBody AiCandidateRejectRequest request) {
        aiCandidateService.reject(id, request);
        return R.ok();
    }

    /**
     * PM 手动拆分候选卡片
     *
     * <p>一条反馈里包含多个不同问题、被 AI 归成一张卡片时，
     * PM 可将其拆成多张新候选卡片分别确认；同一条反馈允许
     * 同时挂在多张新卡片上。
     *
     * @param id      候选卡片 ID
     * @param request 拆分内容（至少 2 个部分）
     * @return 新生成的候选卡片 ID 列表
     */
    @Operation(summary = "拆分候选卡片",
            description = "把一张混合了多个独立问题的卡片拆成多张新草稿卡片；"
                    + "原卡片转为「已拆分」留档")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @PostMapping("/candidates/{id}/split")
    public R<List<Long>> split(
            @PathVariable Long id,
            @Valid @RequestBody AiCandidateSplitRequest request) {
        return R.ok(aiCandidateService.split(id, request));
    }

    /**
     * 获取拆分弹窗的 AI 预填建议
     *
     * <p>PM 点开「拆分」时调用：对这张卡片的关联反馈重新聚类一次，
     * 把识别出的多个问题分别预填成可编辑的表单内容，作为拆分参考。
     * 只读接口，不产生任何业务数据。
     *
     * @param id 候选卡片 ID
     * @return 预填建议
     */
    @Operation(summary = "获取拆分预填建议",
            description = "复用聚类能力识别卡片内包含的几个独立问题，返回可编辑的预填内容；"
                    + "AI 不可用时返回 available=false，前端退回手动填写")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @GetMapping("/candidates/{id}/split-suggestion")
    public R<AiCandidateSplitSuggestion> splitSuggestion(@PathVariable Long id) {
        return R.ok(aiCandidateService.suggestSplit(id));
    }

    /**
     * PM 合并候选卡片到已有问题
     *
     * <p>不生成新问题：关联反馈挂到目标问题上，原卡片转「已合并」留档。
     *
     * @param id      候选卡片 ID
     * @param request 目标问题与合并说明
     * @return 目标问题 ID
     */
    @Operation(summary = "合并候选卡片",
            description = "把候选卡片并入已有问题（不生成新问题）；"
                    + "关联反馈挂到目标问题上，原卡片转「已合并」留档")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @PostMapping("/candidates/{id}/merge")
    public R<Map<String, Long>> merge(
            @PathVariable Long id,
            @Valid @RequestBody AiCandidateMergeRequest request) {
        Long targetIssueId = aiCandidateService.merge(id, request);
        return R.ok(Map.of("issueId", targetIssueId));
    }

    /**
     * 为已确认问题起草需求说明与验收条件
     *
     * <p>只返回草稿文本，不写入任何正式字段。
     *
     * @param id 问题 ID
     * @return 草稿结果
     */
    @Operation(summary = "起草需求草稿",
            description = "AI 作用点 C；返回草稿供 PM 编辑，不会直接写入正式需求")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @PostMapping("/issues/{id}/draft")
    public R<AiDraftResult> draft(@PathVariable Long id) {
        return R.ok(aiCandidateService.draftForIssue(id));
    }

    /**
     * 为候选卡片预填 AI 起草建议（确认弹窗用）
     *
     * <p>对尚未确认的候选卡片提前走一次 DRAFT 作用点，
     * 返回验收条件等草稿内容供确认弹窗预填。
     * 只返回草稿文本，不写入任何正式字段；
     * AI 不可用时返回 available=false，前端退回手动填写。
     *
     * @param id 候选卡片 ID
     * @return 起草建议
     */
    @Operation(summary = "候选卡片起草建议",
            description = "AI 作用点 C 的候选卡片入口；为确认弹窗预填"
                    + "验收条件，不会写入任何正式字段")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @GetMapping("/candidates/{id}/draft-suggestion")
    public R<AiDraftSuggestion> draftSuggestion(@PathVariable Long id) {
        return R.ok(aiCandidateService.suggestDraft(id));
    }

    /**
     * 分页查询 AI 调用记录
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "查询 AI 调用记录",
            description = "含输入材料、返回原文、耗时与结果，用于事后回查")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @GetMapping("/logs")
    public R<PageResult<AiLogVO>> logPage(@Valid AiLogQueryRequest query) {
        return R.ok(aiCandidateService.logPage(query));
    }

    /**
     * 查询单条 AI 调用详情
     *
     * @param id 日志 ID
     * @return 日志详情
     */
    @Operation(summary = "查询 AI 调用详情",
            description = "返回完整的输入材料与 AI 返回原文")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @GetMapping("/logs/{id}")
    public R<AiLogVO> logDetail(@PathVariable Long id) {
        return R.ok(aiCandidateService.logDetail(id));
    }

    /**
     * 查看 AI 运行状态
     *
     * <p>返回期望实现、是否配置了有效 Key、当前实际会用的实现与模型、
     * 以及降级与故障模拟开关的状态。演示与排障时使用。
     *
     * @return 状态信息
     */
    @Operation(summary = "查看 AI 运行状态",
            description = "返回期望实现、实际实现、模型与降级开关状态")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        return R.ok(aiCandidateService.status());
    }
}
