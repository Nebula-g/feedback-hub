package com.company.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.feedback.entity.Notification;

/**
 * 站内通知 Mapper
 *
 * <p>单表 CRUD 由 BaseMapper 提供，未读数统计用 count 即可，无需自定义 SQL。
 *
 * @author 反馈改进助手项目组
 */
public interface NotificationMapper extends BaseMapper<Notification> {
}
