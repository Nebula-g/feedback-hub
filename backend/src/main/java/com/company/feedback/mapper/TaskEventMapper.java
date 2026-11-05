package com.company.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.feedback.entity.TaskEvent;

/**
 * 任务事件 Mapper（时间线）
 *
 * <p>本表为追加写（append-only），与 AI 日志同属证据类数据，
 * Service 层只 insert，不 update、不 delete。
 *
 * @author 反馈改进助手项目组
 */
public interface TaskEventMapper extends BaseMapper<TaskEvent> {
}
