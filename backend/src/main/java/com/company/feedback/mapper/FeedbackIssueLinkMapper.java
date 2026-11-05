package com.company.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.feedback.entity.FeedbackIssueLink;

/**
 * 反馈与问题关联 Mapper
 *
 * <p>注意：由于 (feedback_id, issue_id) 上有唯一索引，
 * 重复关联会抛出 MySQL 唯一键冲突异常，Service 层需捕获并转为友好提示。
 *
 * @author 反馈改进助手项目组
 */
public interface FeedbackIssueLinkMapper extends BaseMapper<FeedbackIssueLink> {
}
