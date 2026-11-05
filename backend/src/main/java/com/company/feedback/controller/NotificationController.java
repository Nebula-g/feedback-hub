package com.company.feedback.controller;

import com.company.feedback.common.PageResult;
import com.company.feedback.common.R;
import com.company.feedback.service.NotificationService;
import com.company.feedback.vo.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知控制器
 *
 * <p>所有接口均基于当前登录用户（UserContext），无需显式传 userId。
 *
 * @author 反馈改进助手项目组
 */
@Tag(name = "站内通知", description = "通知面板与通知中心")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 未读通知数（顶栏角标轮询用）
     *
     * @return 未读数
     */
    @Operation(summary = "未读通知数")
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(notificationService.unreadCount());
    }

    /**
     * 最新通知（顶栏面板用）
     *
     * @param limit 条数，默认 10
     * @return 通知列表
     */
    @Operation(summary = "最新通知列表")
    @GetMapping("/latest")
    public R<PageResult<NotificationVO>> latest(
            @Parameter(description = "条数") @RequestParam(defaultValue = "10") int limit) {
        return R.ok(notificationService.latest(limit));
    }

    /**
     * 分页查询全部通知（通知中心页用）
     *
     * @param pageNo   页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Operation(summary = "分页查询通知")
    @GetMapping
    public R<PageResult<NotificationVO>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(notificationService.page(pageNo, pageSize));
    }

    /**
     * 全部标记已读
     */
    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public R<Void> markAllRead() {
        notificationService.markAllRead();
        return R.ok();
    }

    /**
     * 单条标记已读（点击通知条目时调用）
     *
     * @param id 通知 ID
     */
    @Operation(summary = "单条标记已读")
    @PostMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return R.ok();
    }
}
