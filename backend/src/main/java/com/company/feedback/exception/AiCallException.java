package com.company.feedback.exception;

import com.company.feedback.enums.AiLogStatus;
import lombok.Getter;

/**
 * AI 调用异常
 *
 * <p>把大模型调用过程中所有可能的失败（网络不通、鉴权失败、限流、
 * 超时、返回内容无法解析）统一成一种异常类型，
 * 让上游只需写一个 catch 就能完成降级，避免遗漏某种失败场景。
 *
 * <p><b>为什么自带 logStatus</b>：
 * 超时和普通失败要分开记录——超时往往意味着"阈值设太紧"或"网络抖动"，
 * 而鉴权失败意味着"配置错了"。两者的处置方式完全不同，
 * 混在一起会导致事后无法定位。
 *
 * @author 反馈改进助手项目组
 */
@Getter
public class AiCallException extends RuntimeException {

    /** 该异常对应的日志状态（FAILED / TIMEOUT） */
    private final AiLogStatus logStatus;

    /** 是否为可重试的临时故障（超时、网络抖动） */
    private boolean retryable;

    /**
     * 使用指定日志状态构造异常
     *
     * @param logStatus 日志状态
     * @param message   可读的失败原因（会存入 ai_generation_log.error_msg）
     */
    public AiCallException(AiLogStatus logStatus, String message) {
        super(message);
        this.logStatus = logStatus == null ? AiLogStatus.FAILED : logStatus;
        // 超时属于临时故障，默认可重试
        this.retryable = AiLogStatus.TIMEOUT == this.logStatus;
    }

    /**
     * 使用指定日志状态与原始异常构造
     *
     * @param logStatus 日志状态
     * @param message   可读的失败原因
     * @param cause     原始异常
     */
    public AiCallException(AiLogStatus logStatus, String message,
                           Throwable cause) {
        super(message, cause);
        this.logStatus = logStatus == null ? AiLogStatus.FAILED : logStatus;
        this.retryable = AiLogStatus.TIMEOUT == this.logStatus;
    }

    /**
     * 标记为可重试的临时故障
     *
     * <p>采用流式写法，便于在 throw 处一行表达"这个失败值得重试一次"。
     *
     * @return 当前异常实例
     */
    public AiCallException retryable() {
        this.retryable = true;
        return this;
    }

    /**
     * 是否为超时类异常
     *
     * @return 超时返回 true
     */
    public boolean isTimeout() {
        return AiLogStatus.TIMEOUT == logStatus;
    }

    /**
     * 是否为可重试的临时故障
     *
     * @return 可重试返回 true
     */
    public boolean isRetryable() {
        return retryable;
    }
}
