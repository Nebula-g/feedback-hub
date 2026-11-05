package com.company.feedback.controller;

import com.company.feedback.common.R;
import com.company.feedback.common.UserContext;
import com.company.feedback.dto.FeedbackBatchImportRequest;
import com.company.feedback.dto.FeedbackCreateRequest;
import com.company.feedback.dto.FeedbackQueryRequest;
import com.company.feedback.dto.FeedbackStatusUpdateRequest;
import com.company.feedback.service.FeedbackService;
import com.company.feedback.vo.FeedbackVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 反馈控制器
 *
 * <p><b>权限矩阵（对应需求文档第 4.2 节）</b>：
 * <pre>
 *   接口                CS    PM    DEV   ADMIN
 *   录入反馈            ✓     ✗     ✗     ✓
 *   批量导入            ✓     ✗     ✗     ✓
 *   查看列表            ✓     ✓     ✓     ✓
 *   查看详情            ✓     ✓     ✓     ✓
 *   变更归类状态        ✗     ✓     ✗     ✓
 * </pre>
 *
 * <p><b>请注意：本控制器中没有「修改反馈原文」的接口</b>。
 * 这不是功能缺失，而是本项目的核心业务规则——原始反馈一经录入不可修改。
 *
 * @author 反馈改进助手项目组
 */
@Tag(name = "反馈管理", description = "客服录入反馈、全员查询反馈")
@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 录入单条反馈
     *
     * @param request 录入参数
     * @return 新建反馈的 ID
     */
    @Operation(summary = "录入反馈", description = "客服录入一条脱敏后的用户反馈")
    @PreAuthorize("hasAnyRole('CS', 'ADMIN')")
    @PostMapping
    public R<Map<String, Long>> create(@Valid @RequestBody FeedbackCreateRequest request) {
        Long id = feedbackService.create(request);
        return R.ok(Map.of("id", id));
    }

    /**
     * 批量导入反馈
     *
     * @param request 批量导入参数
     * @return 新建反馈的 ID 列表
     */
    @Operation(summary = "批量导入反馈",
            description = "一次性导入多条反馈，单次最多 200 条，全成功或全失败")
    @PreAuthorize("hasAnyRole('CS', 'ADMIN')")
    @PostMapping("/batch")
    public R<Map<String, Object>> batchImport(
            @Valid @RequestBody FeedbackBatchImportRequest request) {

        List<Long> ids = feedbackService.batchImport(request);
        return R.ok(Map.of("ids", ids, "count", ids.size()));
    }

    /**
     * 分页查询反馈列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "查询反馈列表", description = "支持按模块、状态、来源、关键字筛选")
    @GetMapping
    public R<?> page(@Valid FeedbackQueryRequest query) {
        return R.ok(feedbackService.page(query));
    }

    /**
     * 查询反馈详情
     *
     * @param id 反馈 ID
     * @return 反馈详情
     */
    @Operation(summary = "查询反馈详情", description = "查看反馈原文与归类状态")
    @GetMapping("/{id}")
    public R<FeedbackVO> detail(@PathVariable Long id) {
        return R.ok(feedbackService.detail(id));
    }

    /**
     * 变更反馈归类状态
     *
     * <p>仅产品经理可执行。状态流转需符合预定义规则，
     * 非法跳转将返回 409 冲突。
     *
     * @param id      反馈 ID
     * @param request 状态变更参数
     * @return 操作结果
     */
    @Operation(summary = "变更反馈状态",
            description = "仅产品经理可操作；置为无效时必须填写说明")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id,
                                @Valid @RequestBody FeedbackStatusUpdateRequest request) {
        feedbackService.updateStatus(id, request);
        return R.ok();
    }

    /**
     * 健康检查接口：验证 Token 有效性
     *
     * <p>前端可用此接口判断本地 Token 是否仍然有效。
     *
     * @return 当前用户 ID
     */
    @Operation(summary = "校验登录态", description = "用于前端判断 Token 是否有效")
    @GetMapping("/ping")
    public R<Map<String, Object>> ping() {
        return R.ok(Map.of(
                "userId", UserContext.getUserId(),
                "role", UserContext.getRole()
        ));
    }
}
