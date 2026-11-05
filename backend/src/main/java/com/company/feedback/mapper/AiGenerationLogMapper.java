package com.company.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.feedback.entity.AiGenerationLog;

/**
 * AI 调用日志 Mapper
 *
 * <p>本表为追加写（append-only），Service 层只调用 insert，不提供 update。
 *
 * @author 反馈改进助手项目组
 */
public interface AiGenerationLogMapper extends BaseMapper<AiGenerationLog> {
}
