package com.company.feedback.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内通知实体
 *
 * <p>对应表：notification
 *
 * <p>定位：系统事件推送，机器触发、单人接收。
 * 与「评论」（biz_message）的区别：通知是广播、没有回复动作，
 * 评论是对话、有来回。两者不要混在一张表里。
 *
 * @author 反馈改进助手项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification")
public class Notification extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 接收人 ID（sys_user.id） */
    private Long userId;

    /** 通知类型（NotificationType.code） */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知正文 */
    private String content;

    /** 点击跳转的前端路由，如 /tasks/1；纯知会型通知可为空 */
    private String link;

    /** 是否已读：0未读 1已读 */
    private Integer isRead;
}
