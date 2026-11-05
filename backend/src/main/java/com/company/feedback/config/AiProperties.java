package com.company.feedback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 模块配置属性
 *
 * <p>绑定 application.yml 中 {@code ai.*} 下的所有配置项。
 *
 * <p><b>为什么把 AI 参数全部外置，而不写在代码里</b>：
 * <ol>
 *   <li><b>降级开关要在现场能改</b>：答辩演示时如果需要临时模拟 AI 故障，
 *       改一行配置比改代码重新打包快得多；</li>
 *   <li><b>密钥不能进代码库</b>：api-key 通过 {@code ${AI_API_KEY:...}} 从环境变量读取，
 *       代码库里只留占位符；</li>
 *   <li><b>换模型不改代码</b>：DeepSeek / 通义 / OpenAI 都是 OpenAI 兼容协议，
 *       换供应商只需改 base-url 与 model。</li>
 * </ol>
 *
 * @author 反馈改进助手项目组
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * 期望的实现类型：llm-真实大模型  rule-本地规则兜底
     *
     * <p>注意这只是"期望值"。真实使用哪个实现，
     * 由 {@code AiCandidateServiceImpl} 在运行期根据连通性决定，
     * 结果会写入 ai_generation_log.provider 留证。
     */
    private String provider = "rule";

    /** 大模型相关配置 */
    private Llm llm = new Llm();

    /** 降级与故障模拟相关配置 */
    private Fallback fallback = new Fallback();

    /**
     * 大模型配置
     */
    @Data
    public static class Llm {

        /** OpenAI 兼容协议的接口根地址，如 https://api.deepseek.com */
        private String baseUrl = "https://api.deepseek.com";

        /** API Key（从环境变量注入，代码库只保留占位符） */
        private String apiKey = "YOUR_API_KEY";

        /** 模型名称，如 deepseek-chat */
        private String model = "deepseek-chat";

        /** 单次调用超时时间（毫秒） */
        private int timeoutMs = 30000;

        /**
         * 采样温度
         *
         * <p>分类与归纳任务需要稳定输出，因此取低值。
         * 温度越高越有创造性，但同一批输入可能给出不同归纳结果，
         * 不利于复盘。
         */
        private double temperature = 0.2;

        /** 最大重试次数（不含首次调用） */
        private int maxRetry = 1;

        /**
         * 单次请求最多携带多少条反馈原文
         *
         * <p>防止一次请求的 prompt 过长导致超时或超 token。
         * 超出部分会在下一批继续处理。
         */
        private int maxFeedbackPerRequest = 30;
    }

    /**
     * 降级配置
     */
    @Data
    public static class Fallback {

        /**
         * 是否在 AI 调用失败时自动降级为本地规则实现
         *
         * <p>关掉它之后，AI 失败会直接返回 503 给前端，
         * 适合演示「AI 完全不可用」的极端情况。
         */
        private boolean enabled = true;

        /**
         * 【演示开关】强制模拟 AI 故障
         *
         * <p>置为 true 后，即便配置了合法的 API Key，
         * 大模型实现也会立即抛出异常，用于现场演示降级路径
         * （ai_generation_log.status = FALLBACK）。
         */
        private boolean simulateFailure = false;
    }

    /**
     * 判断当前配置是否具备调用真实大模型的条件
     *
     * <p>判定标准：期望 provider 为 llm、baseUrl 非空、
     * 且 apiKey 不是占位符。三者缺一不可。
     *
     * <p><b>为什么在这里做判断</b>：
     * 如果不判断就用占位符去调，每次都要等一次网络超时才能降级，
     * 演示体验很差。前置判断可以"秒降级"。
     *
     * @return 可以调用大模型返回 true
     */
    public boolean isLlmAvailable() {
        if (!"llm".equalsIgnoreCase(provider)) {
            return false;
        }
        if (llm == null || llm.getBaseUrl() == null || llm.getBaseUrl().isBlank()) {
            return false;
        }
        String key = llm.getApiKey();
        return key != null && !key.isBlank()
                && !"YOUR_API_KEY".equalsIgnoreCase(key.trim());
    }
}
