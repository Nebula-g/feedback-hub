package com.company.feedback.common;

import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一响应封装类
 *
 * <p>所有 Controller 的返回值统一包装为 R，结构如下：
 * <pre>
 * {
 *   "code": 200,
 *   "message": "OK",
 *   "data": { },
 *   "traceId": "a1b2c3d4",
 *   "timestamp": 1761475200000
 * }
 * </pre>
 *
 * <p>为什么需要 traceId：
 * 当 AI 调用出现问题时，前端只需提供 traceId，
 * 就能在后端日志和 ai_generation_log 表中定位到完整调用链路。
 *
 * @param <T> 业务数据类型
 * @author 反馈改进助手项目组
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日志链路追踪 ID 的 KEY */
    private static final String TRACE_ID = "traceId";

    /** 业务状态码 */
    private int code;

    /** 提示信息（可直接展示给用户） */
    private String message;

    /** 业务数据 */
    private T data;

    /** 链路追踪 ID */
    private String traceId;

    /** 响应时间戳（毫秒） */
    private long timestamp;

    /**
     * 私有构造，统一通过静态工厂方法创建
     */
    private R() {
        this.timestamp = System.currentTimeMillis();
        // 从 MDC 中取出当前请求的 traceId，便于前后端联调定位问题
        this.traceId = MDC.get(TRACE_ID);
    }

    // ==================== 成功 ====================

    /**
     * 成功（无返回数据）
     *
     * @param <T> 数据类型
     * @return 统一响应对象
     */
    public static <T> R<T> ok() {
        return ok(null);
    }

    /**
     * 成功（带返回数据）
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 统一响应对象
     */
    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(ResultCode.SUCCESS.getCode());
        r.setMessage(ResultCode.SUCCESS.getMessage());
        r.setData(data);
        return r;
    }

    // ==================== 失败 ====================

    /**
     * 使用预定义错误码构造失败响应
     *
     * @param resultCode 错误码枚举
     * @param <T>        数据类型
     * @return 统一响应对象
     */
    public static <T> R<T> fail(ResultCode resultCode) {
        R<T> r = new R<>();
        r.setCode(resultCode.getCode());
        r.setMessage(resultCode.getMessage());
        return r;
    }

    /**
     * 使用预定义错误码 + 自定义提示信息
     *
     * @param resultCode  错误码枚举
     * @param customMessage 自定义提示信息
     * @param <T>         数据类型
     * @return 统一响应对象
     */
    public static <T> R<T> fail(ResultCode resultCode, String customMessage) {
        R<T> r = new R<>();
        r.setCode(resultCode.getCode());
        r.setMessage(customMessage);
        return r;
    }

    /**
     * 使用自定义状态码构造响应
     *
     * <p>为什么需要这个工厂方法：
     * 全局异常处理器需要把各种框架的异常码（如 500、400）
     * 包装成统一结构，而这些码不属于 {@link ResultCode} 的业务语义范畴。
     * 由于构造方法是私有的（保证只能通过工厂方法创建），
     * 这里提供一个按任意状态码构造的入口。
     *
     * @param code    状态码
     * @param message 提示信息
     * @param <T>     数据类型
     * @return 统一响应对象
     */
    public static <T> R<T> build(int code, String message) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}
