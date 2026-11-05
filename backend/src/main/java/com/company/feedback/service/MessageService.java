package com.company.feedback.service;

import com.company.feedback.dto.MessageSendRequest;
import com.company.feedback.vo.MessageGroupVO;
import com.company.feedback.vo.MessageVO;

import java.util.List;

/**
 * 业务对象评论服务
 *
 * <p>定位：围绕反馈 / 任务的轻量评论流。
 * 发表评论后自动通知对象相关人（录入人 / 负责人），不通知自己。
 *
 * @author 反馈改进助手项目组
 */
public interface MessageService {

    /**
     * 查询某业务对象下的评论时间线（正序）
     *
     * @param bizType 业务对象类型
     * @param bizId   业务对象 ID
     * @return 评论列表
     */
    List<MessageVO> listByBiz(String bizType, Long bizId);

    /**
     * 发表评论
     *
     * <p>评论发出后给对象相关人补一条通知：
     * 反馈 → 通知录入人；任务 → 通知负责人与创建人（均排除自己）。
     *
     * @param request 评论请求
     * @return 新评论 ID
     */
    Long send(MessageSendRequest request);

    /**
     * 当前用户相关的未读评论，按业务对象聚合
     *
     * @return 聚合列表（最多 20 组）
     */
    List<MessageGroupVO> unreadGroups();

    /**
     * 标记某业务对象的评论为已读（打开详情页时调用）
     *
     * @param bizType 业务对象类型
     * @param bizId   业务对象 ID
     */
    void markRead(String bizType, Long bizId);
}
