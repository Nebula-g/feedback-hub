package com.company.feedback.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务对象评论实体
 *
 * <p>对应表：biz_message
 *
 * <p>定位：围绕某个业务对象（反馈 / 任务）的留言时间线，
 * 类似轻量评论流，不做完整 IM 会话模型。
 *
 * <p>已读语义：is_read 以「业务对象」为单位——
 * 相关人打开过该对象的评论区即整批置为已读，
 * 发言人自己的发言不参与未读统计。
 *
 * @author 反馈改进助手项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_message")
public class BizMessage extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 业务对象类型：FEEDBACK / TASK */
    private String bizType;

    /** 业务对象 ID */
    private Long bizId;

    /** 发言人 ID（sys_user.id） */
    private Long senderId;

    /** 评论内容 */
    private String content;

    /** 是否已被对象相关人查看：0未读 1已读 */
    private Integer isRead;
}
