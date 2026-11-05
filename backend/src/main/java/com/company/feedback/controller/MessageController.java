package com.company.feedback.controller;

import com.company.feedback.common.R;
import com.company.feedback.dto.MessageSendRequest;
import com.company.feedback.service.MessageService;
import com.company.feedback.vo.MessageGroupVO;
import com.company.feedback.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务对象评论控制器
 *
 * <p>评论挂在反馈 / 任务详情页下，顶栏「消息」按钮
 * 展示的是按对象聚合的未读评论入口。
 *
 * @author 反馈改进助手项目组
 */
@Tag(name = "业务评论", description = "反馈/任务下的评论留言")
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 查询某业务对象的评论时间线（详情页评论区）
     *
     * @param bizType 业务对象类型
     * @param bizId   业务对象 ID
     * @return 评论列表（正序）
     */
    @Operation(summary = "评论时间线")
    @GetMapping
    public R<List<MessageVO>> list(
            @Parameter(description = "业务对象类型") @RequestParam String bizType,
            @Parameter(description = "业务对象 ID") @RequestParam Long bizId) {
        return R.ok(messageService.listByBiz(bizType, bizId));
    }

    /**
     * 发表评论
     *
     * @param request 评论请求
     * @return 新评论 ID
     */
    @Operation(summary = "发表评论")
    @PostMapping
    public R<Long> send(@Valid @RequestBody MessageSendRequest request) {
        return R.ok(messageService.send(request));
    }

    /**
     * 未读评论按业务对象聚合（顶栏消息面板用）
     *
     * @return 聚合列表
     */
    @Operation(summary = "未读评论聚合")
    @GetMapping("/unread-groups")
    public R<List<MessageGroupVO>> unreadGroups() {
        return R.ok(messageService.unreadGroups());
    }

    /**
     * 标记某业务对象评论已读（打开详情页时调用）
     *
     * @param bizType 业务对象类型
     * @param bizId   业务对象 ID
     */
    @Operation(summary = "标记已读")
    @PostMapping("/read")
    public R<Void> markRead(
            @RequestParam String bizType,
            @RequestParam Long bizId) {
        messageService.markRead(bizType, bizId);
        return R.ok();
    }
}
