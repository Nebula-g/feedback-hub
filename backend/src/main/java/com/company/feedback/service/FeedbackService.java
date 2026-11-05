package com.company.feedback.service;

import com.company.feedback.common.PageResult;
import com.company.feedback.dto.FeedbackBatchImportRequest;
import com.company.feedback.dto.FeedbackCreateRequest;
import com.company.feedback.dto.FeedbackQueryRequest;
import com.company.feedback.dto.FeedbackStatusUpdateRequest;
import com.company.feedback.vo.FeedbackVO;

import java.util.List;

/**
 * 反馈服务接口
 *
 * <p><b>核心业务约束（所有实现必须遵守）</b>：
 * <ul>
 *   <li>反馈原文 content 一经录入不可修改，本接口<b>不存在任何更新原文的方法</b></li>
 *   <li>只有 PM 可以改变反馈的归类状态</li>
 *   <li>状态流转必须遵守 {@link com.company.feedback.enums.FeedbackStatus} 定义的规则</li>
 * </ul>
 *
 * @author 反馈改进助手项目组
 */
public interface FeedbackService {

    /**
     * 录入单条反馈
     *
     * @param request 录入参数
     * @return 新建反馈的 ID
     */
    Long create(FeedbackCreateRequest request);

    /**
     * 批量导入反馈
     *
     * @param request 批量导入参数
     * @return 新建反馈的 ID 列表
     */
    List<Long> batchImport(FeedbackBatchImportRequest request);

    /**
     * 分页查询反馈列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<FeedbackVO> page(FeedbackQueryRequest query);

    /**
     * 查询反馈详情
     *
     * @param id 反馈 ID
     * @return 反馈视图对象
     */
    FeedbackVO detail(Long id);

    /**
     * 变更反馈归类状态
     *
     * @param id      反馈 ID
     * @param request 状态变更参数
     */
    void updateStatus(Long id, FeedbackStatusUpdateRequest request);
}
