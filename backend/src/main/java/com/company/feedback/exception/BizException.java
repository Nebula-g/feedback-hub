package com.company.feedback.exception;

import com.company.feedback.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 *
 * <p>用于表示"业务规则不允许"的情况，例如：
 * <ul>
 *   <li>非法的状态跳转：待开发 → 已完成</li>
 *   <li>越权操作：客服尝试设定任务优先级</li>
 *   <li>数据不存在或不属于当前用户</li>
 * </ul>
 *
 * <p>与系统异常（如空指针）的区别：
 * 业务异常是<b>可预期的</b>，其 message 可以直接展示给终端用户；
 * 系统异常则应统一提示"服务器内部错误"，避免泄露实现细节。
 *
 * @author 反馈改进助手项目组
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务状态码 */
    private final int code;

    /**
     * 使用错误码枚举构造异常
     *
     * @param resultCode 错误码枚举
     */
    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 使用错误码枚举 + 自定义提示信息
     *
     * @param resultCode    错误码枚举
     * @param customMessage 自定义提示信息（会展示给用户）
     */
    public BizException(ResultCode resultCode, String customMessage) {
        super(customMessage);
        this.code = resultCode.getCode();
    }
}
