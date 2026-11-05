package com.company.feedback.service;

import com.company.feedback.common.PageResult;
import com.company.feedback.dto.*;
import com.company.feedback.vo.AiCandidateVO;
import com.company.feedback.vo.AiLogVO;

import java.util.List;
import java.util.Map;

/**
 * AI 候选问题卡片服务接口
 *
 * <p><b>本接口是「AI 产出」与「人工决策」之间的那道闸门</b>。
 *
 * <p>AI 生成的内容只会落在 ai_candidate_issue 里（草稿），
 * 只有 PM 调用 {@link #confirm} 之后，内容才会被搬运进 issue（正式事实）。
 * 除 confirm 之外，本接口不写入任何正式业务表。
 *
 * <p><b>降级承诺</b>：
 * 大模型调用失败时，若 {@code ai.fallback.enabled=true}，
 * 自动改用本地规则实现并正常返回结果，同时在日志里标记 FALLBACK；
 * 若未启用降级，则抛出 503，由前端引导用户走手工流程。
 * 无论哪种情况，ai_generation_log 都会留下完整记录。
 *
 * @author 反馈改进助手项目组
 */
public interface AiCandidateService {

    /**
     * 生成候选问题卡片
     *
     * <p>流程：取出待归类反馈 → 调用 AI 聚类（必要时降级）→ 落库草稿 → 落库日志。
     *
     * @param request 生成参数
     * @return 生成结果
     */
    AiGenerateResult generate(AiGenerateRequest request);

    /**
     * 分页查询候选卡片
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AiCandidateVO> page(AiCandidateQueryRequest query);

    /**
     * PM 确认候选卡片，生成正式问题
     *
     * <p>这是唯一一个把 AI 草稿变成业务事实的入口。
     * AI 原话会被原样保留在 issue.ai_draft_content 中，永不覆盖。
     *
     * @param id      候选卡片 ID
     * @param request PM 确认的内容
     * @return 生成的正式问题 ID
     */
    Map<String, Long> confirm(Long id, AiCandidateConfirmRequest request);

    /**
     * PM 驳回候选卡片
     *
     * @param id      候选卡片 ID
     * @param request 驳回原因
     */
    void reject(Long id, AiCandidateRejectRequest request);

    /**
     * PM 手动拆分候选卡片
     *
     * <p>用于"一条反馈包含多个不同问题被 AI 归成一张卡片"的场景：
     * PM 把原卡片按问题改写成 N 张新 DRAFT 卡片（同一反馈允许
     * 同时挂在多张新卡片上），原卡片转为 SPLITTED 留档。
     * 新卡片随后走正常的确认流程，各自生成正式问题。
     *
     * @param id      候选卡片 ID
     * @param request 拆分内容（至少 2 个部分）
     * @return 新生成的候选卡片 ID 列表
     */
    List<Long> split(Long id, AiCandidateSplitRequest request);

    /**
     * 为拆分弹窗预填 AI 识别结果（只读，不落任何业务数据）
     *
     * <p>对候选卡片的关联反馈重新聚类一次：若识别出多个独立问题，
     * 就按问题给出多份预填内容（标题/现象/诉求/反馈归属），
     * 作为 PM 拆分的参考草稿；PM 可以任意修改后再提交。
     *
     * <p>AI 认为"就一个问题"或调用失败时都不会报错，
     * 而是通过 {@code splitSuggested=false} / {@code available=false}
     * 让前端退回到手动填写。
     *
     * @param id 候选卡片 ID
     * @return 预填建议（含可用性与判断说明）
     */
    AiCandidateSplitSuggestion suggestSplit(Long id);

    /**
     * PM 合并候选卡片到已有问题
     *
     * <p>用于"这张卡片描述的问题已经存在"的场景：
     * 关联反馈挂到目标问题上（复用手工关联的幂等逻辑），
     * 原卡片转为 MERGED 留档，<b>不生成新问题</b>。
     * 这是 CandidateStatus.MERGED 唯一的进入入口。
     *
     * @param id      候选卡片 ID
     * @param request 目标问题与合并说明
     * @return 目标问题 ID
     */
    Long merge(Long id, AiCandidateMergeRequest request);

    /**
     * 分页查询 AI 调用日志
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AiLogVO> logPage(AiLogQueryRequest query);

    /**
     * 查询单条 AI 调用日志详情（含完整输入输出原文）
     *
     * @param id 日志 ID
     * @return 日志详情
     */
    AiLogVO logDetail(Long id);

    /**
     * 为已确认问题起草需求说明与验收条件（AI 作用点 C）
     *
     * <p><b>关键：本方法只返回草稿，不写入 issue.final_content</b>。
     * 是否采纳、如何修改，完全由 PM 决定。
     *
     * @param issueId 问题 ID
     * @return 草稿结果
     */
    AiDraftResult draftForIssue(Long issueId);

    /**
     * 为确认弹窗预填 AI 起草建议（只读，不落任何业务数据）
     *
     * <p>对尚未确认的候选卡片提前走一次 DRAFT 作用点：
     * 用卡片标题/模块/现象/诉求与关联反馈原文构造临时输入，
     * 复用 {@code AiService#draft} 返回需求说明与验收条件草稿。
     *
     * <p>调用失败时不会报错，而是通过 {@code available=false}
     * 让前端保持空字段、提示手动填写——预填只是参考，
     * 不能挡住 PM 的确认操作。
     *
     * @param id 候选卡片 ID
     * @return 起草建议（含可用性与来源说明）
     */
    AiDraftSuggestion suggestDraft(Long id);

    /**
     * 查看当前 AI 实现状态（用于演示与排障）
     *
     * @return 状态信息：期望实现、是否可用、模型名称、降级开关
     */
    Map<String, Object> status();
}
