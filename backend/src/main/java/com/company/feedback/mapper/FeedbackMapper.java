package com.company.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.feedback.entity.Feedback;

/**
 * 原始反馈 Mapper
 *
 * <p><b>重要约定</b>：本 Mapper 虽然继承了 update 系列方法，
 * 但 Service 层<b>不会调用任何会修改 content 字段的更新操作</b>。
 * 仅允许更新 status（归类状态）和 deleted（逻辑删除）。
 * 这是「原始反馈只读」这一业务规则在数据访问层的落地保障。
 *
 * @author 反馈改进助手项目组
 */
public interface FeedbackMapper extends BaseMapper<Feedback> {
}
