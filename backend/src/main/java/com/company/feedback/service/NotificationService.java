package com.company.feedback.service;

import com.company.feedback.common.PageResult;
import com.company.feedback.enums.NotificationType;
import com.company.feedback.vo.NotificationVO;

/**
 * 站内通知服务
 *
 * <p>对外暴露两类能力：
 * <ul>
 *   <li>发送（业务埋点调用）——失败只记日志，绝不影响主业务事务</li>
 *   <li>查询与已读（通知中心调用）</li>
 * </ul>
 *
 * @author 反馈改进助手项目组
 */
public interface NotificationService {

    /**
     * 给单个用户发送通知
     *
     * <p>内部吞掉所有异常：通知属于锦上添花，
     * 不能因为「写一条通知失败」让「指派任务」整个回滚。
     *
     * @param userId  接收人 ID，为空则跳过
     * @param type    通知类型
     * @param title   标题
     * @param content 正文
     * @param link    跳转路由，可为空
     */
    void send(Long userId, NotificationType type, String title, String content, String link);

    /**
     * 给某个角色的全部用户发送通知
     *
     * @param role           角色代码，如 PM
     * @param type           通知类型
     * @param title          标题
     * @param content        正文
     * @param link           跳转路由
     * @param excludeUserId  需要排除的用户 ID（通常是触发人自己），可为空
     */
    void sendToRole(String role, NotificationType type, String title,
                    String content, String link, Long excludeUserId);

    /**
     * 查询当前用户未读通知数
     *
     * @return 未读数
     */
    long unreadCount();

    /**
     * 查询当前用户最新通知（通知面板用）
     *
     * @param limit 条数
     * @return 通知列表，按时间倒序
     */
    PageResult<NotificationVO> latest(int limit);

    /**
     * 分页查询当前用户全部通知（通知中心页用）
     *
     * @param pageNo   页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<NotificationVO> page(int pageNo, int pageSize);

    /**
     * 当前用户全部通知标记已读
     */
    void markAllRead();

    /**
     * 单条通知标记已读
     *
     * @param id 通知 ID
     */
    void markRead(Long id);
}
