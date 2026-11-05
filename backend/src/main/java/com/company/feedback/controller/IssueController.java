package com.company.feedback.controller;

import com.company.feedback.common.R;
import com.company.feedback.dto.IssueCreateRequest;
import com.company.feedback.dto.IssueQueryRequest;
import com.company.feedback.dto.LinkFeedbackRequest;
import com.company.feedback.service.IssueService;
import com.company.feedback.vo.IssueVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 问题控制器
 *
 * <p><b>权限矩阵</b>：
 * <pre>
 *   接口              CS    PM    DEV   ADMIN
 *   手工创建问题      ✗     ✓     ✗     ✗
 *   查询问题列表      ✓     ✓     ✓     ✓
 *   查询问题详情      ✓     ✓     ✓     ✓
 *   关联反馈          ✗     ✓     ✗     ✗
 *   解除关联          ✗     ✓     ✗     ✗
 * </pre>
 *
 * <p>为什么只有 PM 能创建/关联：
 * 问题的定义与归类属于产品决策范畴。
 * 客服只提供原始素材，开发只负责执行，
 * 都不应决定"这算不算一个问题、该归到哪一类"。
 *
 * @author 反馈改进助手项目组
 */
@Tag(name = "问题管理", description = "已确认问题的创建、查询与反馈关联")
@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    /**
     * PM 手工创建问题
     *
     * @param request 创建参数
     * @return 新建问题的 ID
     */
    @Operation(summary = "手工创建问题",
            description = "PM 直接创建问题，来源标记为 MANUAL")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @PostMapping
    public R<Map<String, Long>> create(@Valid @RequestBody IssueCreateRequest request) {
        return R.ok(Map.of("id", issueService.create(request)));
    }

    /**
     * 分页查询问题列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "查询问题列表", description = "支持按模块、来源、关键字筛选")
    @GetMapping
    public R<?> page(@Valid IssueQueryRequest query) {
        return R.ok(issueService.page(query));
    }

    /**
     * 查询问题详情
     *
     * <p>详情页会同时返回 AI 草稿与 PM 定稿，
     * 前端可据此展示"AI 建议 → 人工确认"的对比。
     *
     * @param id 问题 ID
     * @return 问题详情
     */
    @Operation(summary = "查询问题详情",
            description = "含 AI 草稿与 PM 定稿，可对比差异")
    @GetMapping("/{id}")
    public R<IssueVO> detail(@PathVariable Long id) {
        return R.ok(issueService.detail(id));
    }

    /**
     * 手工关联反馈到问题
     *
     * @param id      问题 ID
     * @param request 关联参数
     * @return 实际新增的关联条数
     */
    @Operation(summary = "关联反馈",
            description = "PM 手工将反馈归入该问题；已关联的反馈自动跳过")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @PostMapping("/{id}/feedbacks")
    public R<Map<String, Object>> linkFeedbacks(
            @PathVariable Long id,
            @Valid @RequestBody LinkFeedbackRequest request) {

        int count = issueService.linkFeedbacks(id, request);
        return R.ok(Map.of("linkedCount", count));
    }

    /**
     * 解除反馈与问题的关联
     *
     * @param issueId    问题 ID
     * @param feedbackId 反馈 ID
     * @return 操作结果
     */
    @Operation(summary = "解除反馈关联",
            description = "若解除后该反馈无任何关联，其状态回退为未归类")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @DeleteMapping("/{id}/feedbacks/{feedbackId}")
    public R<Void> unlinkFeedback(@PathVariable("id") Long issueId,
                                  @PathVariable Long feedbackId) {
        issueService.unlinkFeedback(issueId, feedbackId);
        return R.ok();
    }
}
