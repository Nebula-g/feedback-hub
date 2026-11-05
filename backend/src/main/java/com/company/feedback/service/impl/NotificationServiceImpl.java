package com.company.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.feedback.common.PageResult;
import com.company.feedback.entity.Notification;
import com.company.feedback.entity.SysUser;
import com.company.feedback.enums.NotificationType;
import com.company.feedback.mapper.NotificationMapper;
import com.company.feedback.mapper.SysUserMapper;
import com.company.feedback.service.NotificationService;
import com.company.feedback.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内通知服务实现
 *
 * <p><b>核心取舍：发送通知绝不抛异常</b>。
 * 通知是主业务的附属品——「任务指派成功但通知失败」
 * 远好于「因为通知表故障导致指派整体回滚」。
 * 因此 send / sendToRole 全程 try-catch，失败只打 WARN 日志。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final SysUserMapper sysUserMapper;

    // ==================== 发送（业务埋点调用） ====================

    /** {@inheritDoc} */
    @Override
    public void send(Long userId, NotificationType type, String title,
                     String content, String link) {
        if (userId == null) {
            return;
        }
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type.getCode());
            n.setTitle(title);
            n.setContent(content);
            n.setLink(link);
            n.setIsRead(0);
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("发送通知失败（不影响主业务）, userId={}, type={}", userId, type, e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void sendToRole(String role, NotificationType type, String title,
                           String content, String link, Long excludeUserId) {
        try {
            List<SysUser> users = sysUserMapper.selectList(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, role));
            for (SysUser user : users) {
                if (user.getId().equals(excludeUserId)) {
                    continue;
                }
                send(user.getId(), type, title, content, link);
            }
        } catch (Exception e) {
            log.warn("按角色发送通知失败（不影响主业务）, role={}", role, e);
        }
    }

    // ==================== 查询与已读 ====================

    /** {@inheritDoc} */
    @Override
    public long unreadCount() {
        Long userId = currentUserId();
        Long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
        return count == null ? 0 : count;
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<NotificationVO> latest(int limit) {
        return page(1, limit);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<NotificationVO> page(int pageNo, int pageSize) {
        Long userId = currentUserId();
        IPage<Notification> page = notificationMapper.selectPage(
                new Page<>(pageNo, Math.min(pageSize, 100)),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getId));
        return PageResult.of(page, this::toVO);
    }

    /** {@inheritDoc} */
    @Override
    public void markAllRead() {
        Long userId = currentUserId();
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
                        .set(Notification::getIsRead, 1));
    }

    /** {@inheritDoc} */
    @Override
    public void markRead(Long id) {
        Long userId = currentUserId();
        // 带 user_id 条件：防止越权把别人的通知标成已读
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getId, id)
                        .eq(Notification::getUserId, userId)
                        .set(Notification::getIsRead, 1));
    }

    // ==================== 私有工具 ====================

    private Long currentUserId() {
        return com.company.feedback.common.UserContext.getUserId();
    }

    private NotificationVO toVO(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setType(n.getType());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setLink(n.getLink());
        vo.setIsRead(n.getIsRead() != null && n.getIsRead() == 1);
        vo.setCreateTime(n.getCreateTime());
        return vo;
    }
}
