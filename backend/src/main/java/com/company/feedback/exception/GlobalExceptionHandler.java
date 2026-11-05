package com.company.feedback.exception;

import com.company.feedback.common.R;
import com.company.feedback.common.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>职责：把所有异常统一转换为 {@link R} 响应结构，
 * 保证前端永远只需要处理一种响应格式。
 *
 * <p><b>关键设计：HTTP 状态码与业务码保持一致</b>
 * <pre>
 *   400 参数校验失败 / 请求格式错误
 *   401 未登录或 Token 失效
 *   403 角色无权访问（越权）
 *   404 资源不存在
 *   409 状态机冲突 / 并发冲突
 *   500 未预期的系统异常
 *   503 AI 服务不可用（已降级）
 * </pre>
 *
 * <p><b>为什么不统一返回 HTTP 200</b>：
 * 本项目有一条明确的验收要求——前端要区分「失败」和「冲突」两种状态。
 * 如果所有错误都返回 200，前端只能靠解析 body 里的 code 来判断，
 * axios 的拦截器、浏览器的网络面板、Nginx 的访问日志全都失去意义，
 * 排查问题时要多绕一层。让 HTTP 状态码说真话，
 * 前端一句 {@code if (status === 409)} 就能展示冲突态 UI。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     *
     * <p>业务异常是"可预期的失败"，例如状态机非法跳转、数据不存在。
     * 它的 message 是写给用户看的，可以直接展示。
     *
     * @param e 业务异常
     * @return 统一响应对象，HTTP 状态码与业务码一致
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBizException(BizException e) {
        log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return build(e.getCode(), e.getMessage());
    }

    /**
     * 处理越权访问
     *
     * <p><b>为什么必须单独处理</b>：
     * {@code @PreAuthorize} 抛出的 AccessDeniedException 会先被本处理器捕获，
     * 如果不显式声明，就会落到最后的 Exception 兜底分支，
     * 把一个明确的"无权限"变成"服务器内部错误"，
     * 前端既无法正确提示，日志里也会混入大量无意义的堆栈。
     *
     * <p>这里只记 warn 不记 error：越权是正常的权限校验结果，
     * 不是系统故障，不需要按故障级别报警。
     *
     * @param e 权限不足异常
     * @return 403 响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足：{}", e.getMessage());
        return build(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMessage());
    }

    /**
     * 处理方法参数校验异常（@RequestBody + @Valid）
     *
     * @param e 参数校验异常
     * @return 400 响应，message 为各字段错误的拼接
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败：{}", message);
        return build(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 处理表单/查询参数绑定校验异常
     *
     * @param e 绑定异常
     * @return 400 响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<Void>> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return build(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 处理单参数校验异常（@Validated + 基本类型）
     *
     * @param e 约束违反异常
     * @return 400 响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return build(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 处理缺少必填请求参数
     *
     * @param e 缺少参数异常
     * @return 400 响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParam(
            MissingServletRequestParameterException e) {
        return build(ResultCode.BAD_REQUEST.getCode(),
                "缺少必填参数：" + e.getParameterName());
    }

    /**
     * 处理参数类型转换失败（如 id 传了非数字）
     *
     * @param e 类型转换异常
     * @return 400 响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        return build(ResultCode.BAD_REQUEST.getCode(), "参数格式错误：" + e.getName());
    }

    /**
     * 处理请求内容类型不支持
     *
     * @param e 媒体类型异常
     * @return 400 响应
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<R<Void>> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException e) {
        return build(ResultCode.BAD_REQUEST.getCode(),
                "请求体格式不支持，请使用 application/json");
    }

    /**
     * 处理请求方法不支持
     *
     * @param e 方法不支持异常
     * @return 400 响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        return build(ResultCode.BAD_REQUEST.getCode(),
                "请求方法不支持：" + e.getMethod());
    }

    /**
     * 处理上传文件超过大小限制
     *
     * <p>Spring 在进入 Controller 之前就会抛出该异常，
     * 因此必须在这里拦截，否则用户只会看到一句"服务器内部错误"。
     *
     * @param e 超限异常
     * @return 400 响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<R<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return build(ResultCode.BAD_REQUEST.getCode(), "文件大小超过限制（最大 10MB）");
    }

    /**
     * 兜底处理：所有未预期的系统异常
     *
     * <p>关键点：对外只暴露通用提示，完整堆栈只写入日志，
     * 避免向客户端泄露数据库结构、文件路径等敏感信息。
     *
     * @param e 系统异常
     * @return 500 响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return build(ResultCode.INTERNAL_ERROR.getCode(),
                ResultCode.INTERNAL_ERROR.getMessage());
    }

    /**
     * 构造带 HTTP 状态码的错误响应
     *
     * @param code    业务状态码，同时作为 HTTP 状态码
     * @param message 提示信息
     * @return 响应实体
     */
    private ResponseEntity<R<Void>> build(int code, String message) {
        HttpStatus status = HttpStatus.resolve(code);
        if (status == null) {
            // 业务码不在标准 HTTP 状态码表内时，退化为 500，
            // 避免 ResponseEntity 收到非法状态值而抛异常
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(R.build(code, message));
    }
}
