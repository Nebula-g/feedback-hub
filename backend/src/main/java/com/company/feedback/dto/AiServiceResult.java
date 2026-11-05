package com.company.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 调用的统一返回包装
 *
 * <p>把「解析后的业务对象」与「模型返回原文」绑在一起返回，
 * 避免调用方拿到结果后还要回头找原文——
 * 那样一旦中间多了一层方法，原文就会丢失，证据链也就断了。
 *
 * @param <T> 解析后的业务对象类型
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiServiceResult<T> {

    /** 解析后的业务对象 */
    private T data;

    /** 模型返回原文（用于原样落库留证） */
    private String rawOutput;

    /** 本次实际使用的实现类型：LLM / RULE（由调度层回填） */
    private String provider;

    /** 本次实际使用的模型名称（由调度层回填） */
    private String model;

    /** 调用结果：SUCCESS / FAILED / TIMEOUT / FALLBACK（由调度层回填） */
    private String status;

    /** 耗时毫秒（由调度层回填） */
    private Integer costMs;
}
