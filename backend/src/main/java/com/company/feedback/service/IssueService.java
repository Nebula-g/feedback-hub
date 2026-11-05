package com.company.feedback.service;

import com.company.feedback.common.PageResult;
import com.company.feedback.dto.IssueCreateRequest;
import com.company.feedback.dto.IssueQueryRequest;
import com.company.feedback.dto.LinkFeedbackRequest;
import com.company.feedback.entity.Issue;
import com.company.feedback.vo.IssueVO;

/**
 * 问题服务接口
 *
 * <p><b>问题的两种来源</b>：
 * <ol>
 *   <li>MANUAL —— PM 手工创建：aiDraftContent 为空</li>
 *   <li>AI     —— 由 AI 候选卡片确认生成：aiDraftContent 保留 AI 原话</li>
 * </ol>
 * 本接口只处理第 1 种及关联关系的维护；
 * 第 2 种由阶段 4 的候选卡片确认流程调用（预留 confirmFromCandidate 方法）。
 *
 * @author 反馈改进助手项目组
 */
public interface IssueService {

    /**
     * PM 手工创建问题
     *
     * @param request 创建参数
     * @return 新建问题的 ID
     */
    Long create(IssueCreateRequest request);

    /**
     * 分页查询问题列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<IssueVO> page(IssueQueryRequest query);

    /**
     * 查询问题详情
     *
     * @param id 问题 ID
     * @return 问题视图对象
     */
    IssueVO detail(Long id);

    /**
     * 手工关联反馈到问题
     *
     * <p>关联成功后，被关联反馈的状态由 NEW 变为 LINKED。
     *
     * @param id      问题 ID
     * @param request 关联参数
     * @return 实际新增关联的条数
     */
    int linkFeedbacks(Long id, LinkFeedbackRequest request);

    /**
     * 解除反馈与问题的关联
     *
     * <p>解除后，若该反馈不再关联任何问题，其状态回退为 NEW，
     * 重新进入待归类池等待 PM 处理。
     *
     * @param issueId    问题 ID
     * @param feedbackId 反馈 ID
     */
    void unlinkFeedback(Long issueId, Long feedbackId);

    /**
     * 由 AI 候选卡片确认生成正式问题
     *
     * <p><b>本方法是「AI 产出 → 人工确认 → 正式结论」这条链的关键一环</b>：
     * 它会把 AI 生成的原始内容原封不动地带入 aiDraftContent，
     * 把 PM 修改后的内容写入 finalContent，
     * 两者并存，从而让「AI 说了什么、人改成什么」一目了然。
     *
     * <p>阶段 2 先提供签名，实现与 AI 候选卡片流程在阶段 4 一并完成。
     *
     * @param candidateId         候选卡片 ID
     * @param finalTitle          PM 确认后的标题
     * @param finalDesc           PM 确认后的描述
     * @param finalContent        PM 定稿的需求说明
     * @param acceptanceCriteria  PM 填写的验收条件
     * @return 新建问题实体
     */
    Issue confirmFromCandidate(Long candidateId, String finalTitle,
                               String finalDesc, String finalContent,
                               String acceptanceCriteria);
}
