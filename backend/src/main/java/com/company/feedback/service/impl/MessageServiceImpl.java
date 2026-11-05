package com.company.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.feedback.common.ResultCode;
import com.company.feedback.common.UserContext;
import com.company.feedback.dto.MessageSendRequest;
import com.company.feedback.entity.BizMessage;
import com.company.feedback.entity.Feedback;
import com.company.feedback.entity.ImprovementTask;
import com.company.feedback.entity.SysUser;
import com.company.feedback.enums.NotificationType;
import com.company.feedback.exception.BizException;
import com.company.feedback.mapper.BizMessageMapper;
import com.company.feedback.mapper.FeedbackMapper;
import com.company.feedback.mapper.ImprovementTaskMapper;
import com.company.feedback.mapper.SysUserMapper;
import com.company.feedback.service.MessageService;
import com.company.feedback.service.NotificationService;
import com.company.feedback.vo.MessageGroupVO;
import com.company.feedback.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务对象评论服务实现
 *
 * <p>为什么直接注入 NotificationService 不会循环依赖：
 * NotificationServiceImpl 只依赖 Mapper，不反向依赖本服务。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    /** 业务对象类型：反馈 */
    private static final String BIZ_FEEDBACK = "FEEDBACK";

    /** 业务对象类型：任务 */
    private static final String BIZ_TASK = "TASK";

    /** 时间线单次最大返回条数：评论属于查看型数据，超出说明该做历史分页了 */
    private static final int TIMELINE_LIMIT = 200;

    /** 评论摘要长度：通知与聚合列表里展示用 */
    private static final int EXCERPT_LEN = 30;

    private final BizMessageMapper bizMessageMapper;
    private final SysUserMapper sysUserMapper;
    private final FeedbackMapper feedbackMapper;
    private final ImprovementTaskMapper taskMapper;
    private final NotificationService notificationService;

    // ==================== 查询 ====================

    /** {@inheritDoc} */
    @Override
    public List<MessageVO> listByBiz(String bizType, Long bizId) {
        checkBizType(bizType);
        Long me = UserContext.getUserId();

        List<BizMessage> list = bizMessageMapper.selectList(
                new LambdaQueryWrapper<BizMessage>()
                        .eq(BizMessage::getBizType, bizType)
                        .eq(BizMessage::getBizId, bizId)
                        .orderByAsc(BizMessage::getId)
                        .last("LIMIT " + TIMELINE_LIMIT));

        // 批量取发言人姓名：computeIfAbsent 天然去重，同一发言人只查一次
        Map<Long, String> nameCache = new HashMap<>();
        return list.stream().map(m -> {
            MessageVO vo = new MessageVO();
            vo.setId(m.getId());
            vo.setSenderId(m.getSenderId());
            vo.setSenderName(nameCache.computeIfAbsent(m.getSenderId(), this::loadName));
            vo.setContent(m.getContent());
            vo.setMine(m.getSenderId().equals(me));
            vo.setCreateTime(m.getCreateTime());
            return vo;
        }).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<MessageGroupVO> unreadGroups() {
        Long me = UserContext.getUserId();
        List<MessageGroupVO> groups = bizMessageMapper.selectUnreadGroups(me);

        // 组数量上限 20，逐组补标题与摘要可接受；避免为了消 N+1 引入复杂 JOIN
        for (MessageGroupVO g : groups) {
            g.setTitle(loadBizTitle(g.getBizType(), g.getBizId()));
            g.setLastContent(bizMessageMapper.selectLastContent(g.getBizType(), g.getBizId()));
        }
        return groups;
    }

    // ==================== 发表与已读 ====================

    /** {@inheritDoc} */
    @Override
    public Long send(MessageSendRequest request) {
        checkBizType(request.getBizType());

        // 评论前校验对象存在，避免脏数据挂在已删除对象上
        requireBizExists(request.getBizType(), request.getBizId());

        Long me = UserContext.getUserId();

        BizMessage m = new BizMessage();
        m.setBizType(request.getBizType());
        m.setBizId(request.getBizId());
        m.setSenderId(me);
        m.setContent(request.getContent().trim());
        m.setIsRead(0);
        bizMessageMapper.insert(m);

        notifyRelatedUsers(request.getBizType(), request.getBizId(), me);
        return m.getId();
    }

    /** {@inheritDoc} */
    @Override
    public void markRead(String bizType, Long bizId) {
        checkBizType(bizType);
        Long me = UserContext.getUserId();
        bizMessageMapper.markBizRead(bizType, bizId, me);
    }

    // ==================== 私有工具 ====================

    /**
     * 评论发出后通知对象相关人（均排除发言人自己）
     *
     * <p>通知发送失败不影响评论本身，send 内部已吞异常。
     *
     * @param bizType 业务对象类型
     * @param bizId   业务对象 ID
     * @param sender  发言人 ID
     */
    private void notifyRelatedUsers(String bizType, Long bizId, Long sender) {
        String excerpt = excerpt(bizMessageMapper.selectLastContent(bizType, bizId));

        if (BIZ_FEEDBACK.equals(bizType)) {
            Feedback f = feedbackMapper.selectById(bizId);
            if (f == null || f.getCreatedBy() == null
                    || f.getCreatedBy().equals(sender)) {
                return;
            }
            notificationService.send(f.getCreatedBy(),
                    NotificationType.FEEDBACK_STATUS,
                    "反馈有新评论",
                    "有人在你录入的反馈「" + excerpt(f.getContent())
                            + "」下留言：" + excerpt,
                    "/feedbacks/" + bizId);
            return;
        }

        ImprovementTask t = taskMapper.selectById(bizId);
        if (t == null) {
            return;
        }
        if (t.getAssigneeId() != null && !t.getAssigneeId().equals(sender)) {
            notificationService.send(t.getAssigneeId(),
                    NotificationType.TASK_STATUS,
                    "任务有新评论",
                    "有人在你负责的任务「" + t.getTitle() + "」下留言：" + excerpt,
                    "/tasks/" + bizId);
        }
        if (t.getCreatedBy() != null && !t.getCreatedBy().equals(sender)
                && !t.getCreatedBy().equals(t.getAssigneeId())) {
            notificationService.send(t.getCreatedBy(),
                    NotificationType.TASK_STATUS,
                    "任务有新评论",
                    "有人在你创建的任务「" + t.getTitle() + "」下留言：" + excerpt,
                    "/tasks/" + bizId);
        }
    }

    private String loadName(Long userId) {
        SysUser u = sysUserMapper.selectById(userId);
        return u == null ? "未知用户"
                : (StringUtils.hasText(u.getDisplayName()) ? u.getDisplayName() : u.getUsername());
    }

    private String loadBizTitle(String bizType, Long bizId) {
        if (BIZ_FEEDBACK.equals(bizType)) {
            Feedback f = feedbackMapper.selectById(bizId);
            return f == null ? "（反馈已删除）" : excerpt(f.getContent());
        }
        ImprovementTask t = taskMapper.selectById(bizId);
        return t == null ? "（任务已删除）" : t.getTitle();
    }

    private void requireBizExists(String bizType, Long bizId) {
        if (BIZ_FEEDBACK.equals(bizType)) {
            if (feedbackMapper.selectById(bizId) == null) {
                throw new BizException(ResultCode.NOT_FOUND, "反馈不存在或已被删除");
            }
            return;
        }
        if (taskMapper.selectById(bizId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "任务不存在或已被删除");
        }
    }

    private void checkBizType(String bizType) {
        if (!BIZ_FEEDBACK.equals(bizType) && !BIZ_TASK.equals(bizType)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的业务对象类型：" + bizType);
        }
    }

    private String excerpt(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= EXCERPT_LEN ? text : text.substring(0, EXCERPT_LEN) + "…";
    }
}
