package com.company.feedback.service.impl;

import com.company.feedback.config.AiProperties;
import com.company.feedback.dto.AiCandidateSuggestion;
import com.company.feedback.dto.AiClusterResult;
import com.company.feedback.dto.AiDraftResult;
import com.company.feedback.dto.AiExtractItem;
import com.company.feedback.entity.Feedback;
import com.company.feedback.entity.Issue;
import com.company.feedback.enums.AiLogStatus;
import com.company.feedback.enums.AiProvider;
import com.company.feedback.exception.AiCallException;
import com.company.feedback.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 服务 · 真实大模型实现（OpenAI 兼容协议）
 *
 * <p><b>支持的供应商</b>：任何实现了 OpenAI Chat Completions 协议的服务，
 * 包括 DeepSeek、通义千问、月之暗面、OpenAI 等。
 * 切换只需改 application.yml 里的 {@code ai.llm.base-url} 与 {@code model}。
 *
 * <p><b>为什么不用官方 SDK</b>：
 * <ol>
 *   <li>SDK 会引入一堆传递依赖，与"最小依赖"的原则冲突；</li>
 *   <li>不同厂商 SDK 接口不一致，换个供应商就要重写调用层，
 *       违背了"AI 可替换"这一设计目标；</li>
 *   <li>直接用 JDK 自带的 HttpClient，超时与重试可以完全自己掌控。</li>
 * </ol>
 *
 * <p><b>失败处理策略</b>：
 * 所有失败统一抛 {@link AiCallException}，由上游决定是否降级。
 * 本实现<b>不自己吞异常</b>——因为"是否可接受规则兜底的结果"
 * 是业务决策，不是技术决策。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmAiServiceImpl implements AiService {

    /** Jackson 对象映射器（线程安全，可复用） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 每次调用共享的 HTTP 客户端 */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 错误信息最大入库长度，防止异常文本把日志表撑爆 */
    private static final int MAX_ERROR_LEN = 900;

    private final AiProperties aiProperties;

    // ==================================================================
    // 接口实现
    // ==================================================================

    /** {@inheritDoc} */
    @Override
    public String provider() {
        return AiProvider.LLM.getCode();
    }

    /** {@inheritDoc} */
    @Override
    public String model() {
        AiProperties.Llm llm = aiProperties.getLlm();
        return llm == null ? "unknown-model" : llm.getModel();
    }

    /**
     * {@inheritDoc}
     *
     * <p>只检查配置完整性，不做网络探测。
     * 这样可以在发起真实请求之前就完成降级决策，避免不必要的等待。
     */
    @Override
    public boolean available() {
        return aiProperties.isLlmAvailable();
    }

    /**
     * {@inheritDoc}
     *
     * <p>作用点 A：把一段自然语言反馈拆成结构化现象与诉求，
     * 允许"一对多"（用户在一句话里抱怨了两件事）。
     */
    @Override
    public List<AiExtractItem> extract(Feedback feedback) {

        if (feedback == null || !StringUtils.hasText(feedback.getContent())) {
            return List.of(AiExtractItem.builder()
                    .feedbackId(feedback == null ? null : feedback.getId())
                    .module(feedback == null ? null : feedback.getModule())
                    .signature("INSUFFICIENT_EVIDENCE")
                    .phenomenon("反馈内容为空")
                    .userDemand("需客服补充反馈原文")
                    .build());
        }

        String raw = chat(SYSTEM_EXTRACT, buildExtractUserPrompt(feedback));
        JsonNode root = parseJson(raw);

        List<AiExtractItem> items = new ArrayList<>();

        // 模型判定依据不足
        if (root.path("insufficient").asBoolean(false)) {
            items.add(AiExtractItem.builder()
                    .feedbackId(feedback.getId())
                    .module(feedback.getModule())
                    .signature("INSUFFICIENT_EVIDENCE")
                    .phenomenon("依据不足：原文「" + feedback.getContent() + "」"
                            + "缺少可定位的现象描述")
                    .userDemand("需客服补充：具体页面、操作步骤、期望结果与实际结果")
                    .build());
            return items;
        }

        for (JsonNode node : root.path("items")) {
            items.add(AiExtractItem.builder()
                    .feedbackId(feedback.getId())
                    .module(text(node, "module", feedback.getModule()))
                    .signature(text(node, "signature", "UNMATCHED"))
                    .phenomenon(text(node, "phenomenon", feedback.getContent()))
                    .userDemand(text(node, "userDemand", ""))
                    .build());
        }

        if (items.isEmpty()) {
            throw new AiCallException(AiLogStatus.FAILED,
                    "AI 返回内容为空，无法完成提取");
        }

        return items;
    }

    /**
     * {@inheritDoc}
     *
     * <p>作用点 B：把一批反馈归纳成候选问题卡片。
     * 单次请求最多携带 {@code ai.llm.max-feedback-per-request} 条原文，
     * 超出部分自动分批，避免 prompt 过长导致超时。
     */
    @Override
    public AiClusterResult cluster(List<Feedback> feedbacks) {

        if (feedbacks == null || feedbacks.isEmpty()) {
            return AiClusterResult.builder()
                    .candidates(Collections.emptyList())
                    .rawOutput("")
                    .build();
        }

        int batchSize = Math.max(1, aiProperties.getLlm().getMaxFeedbackPerRequest());
        List<AiCandidateSuggestion> result = new ArrayList<>();
        StringBuilder rawBuilder = new StringBuilder();

        for (int from = 0; from < feedbacks.size(); from += batchSize) {
            int to = Math.min(from + batchSize, feedbacks.size());
            List<Feedback> batch = feedbacks.subList(from, to);

            String raw = chat(SYSTEM_CLUSTER, buildClusterUserPrompt(batch));
            rawBuilder.append(raw).append('\n');
            JsonNode root = parseJson(raw);

            for (JsonNode node : root.path("candidates")) {
                result.add(toSuggestion(node));
            }
        }

        if (result.isEmpty()) {
            throw new AiCallException(AiLogStatus.FAILED,
                    "AI 未返回任何候选卡片");
        }

        // 置信度高的排前面
        result.sort(java.util.Comparator.comparing(
                AiCandidateSuggestion::getConfidence,
                java.util.Comparator.reverseOrder()));

        return AiClusterResult.builder()
                .candidates(result)
                .rawOutput(rawBuilder.toString().trim())
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>作用点 C：为已确认问题起草需求说明与验收条件。
     * 结果只作为草稿返回，不会直接写入 issue.final_content。
     */
    @Override
    public AiDraftResult draft(Issue issue, List<String> feedbackContents) {

        if (issue == null) {
            throw new AiCallException(AiLogStatus.FAILED, "问题不存在，无法起草需求");
        }

        String raw = chat(SYSTEM_DRAFT,
                buildDraftUserPrompt(issue, feedbackContents));
        JsonNode root = parseJson(raw);

        String draftContent = root.path("draftContent").asText("");
        String criteria = root.path("acceptanceCriteria").asText("");

        if (!StringUtils.hasText(draftContent)) {
            throw new AiCallException(AiLogStatus.FAILED,
                    "AI 返回的需求草稿为空");
        }

        return AiDraftResult.builder()
                .draftContent(draftContent)
                .acceptanceCriteria(criteria)
                .rawOutput(raw)
                .build();
    }

    // ==================================================================
    // HTTP 调用
    // ==================================================================

    /**
     * 发起一次对话请求，带超时与重试
     *
     * <p><b>重试策略</b>：只对"可恢复的失败"重试，即超时与网络异常；
     * 鉴权失败、参数错误这类"确定性失败"立即抛出，不做无谓重试。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词（含反馈原文）
     * @return 模型返回的文本内容原文
     * @throws AiCallException 调用失败或超时
     */
    private String chat(String systemPrompt, String userPrompt) {

        // 【演示开关】强制模拟 AI 故障，用于答辩展示降级路径
        if (aiProperties.getFallback().isSimulateFailure()) {
            throw new AiCallException(AiLogStatus.FAILED,
                    "演示开关已开启（ai.fallback.simulate-failure=true）：强制模拟 AI 服务不可用");
        }

        AiProperties.Llm llm = aiProperties.getLlm();
        if (llm == null || !StringUtils.hasText(llm.getApiKey())) {
            throw new AiCallException(AiLogStatus.FAILED, "未配置 AI API Key");
        }

        int maxRetry = Math.max(0, llm.getMaxRetry());
        AiCallException last = null;

        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                return doPost(llm, systemPrompt, userPrompt);
            } catch (AiCallException e) {
                last = e;
                // 确定性失败（如鉴权错误）不重试
                if (!e.isTimeout() && !e.isRetryable()) {
                    break;
                }
                log.warn("AI调用失败，准备重试, attempt={}/{}, reason={}",
                        attempt + 1, maxRetry + 1, e.getMessage());
            }
        }

        throw last == null
                ? new AiCallException(AiLogStatus.FAILED, "AI 调用失败")
                : last;
    }

    /**
     * 执行一次 HTTP POST
     *
     * @param llm          大模型配置
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 模型返回内容
     * @throws AiCallException 调用失败
     */
    private String doPost(AiProperties.Llm llm, String systemPrompt,
                          String userPrompt) {

        String body = buildRequestBody(llm, systemPrompt, userPrompt);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(llm.getBaseUrl())
                            + "/chat/completions"))
                    .timeout(Duration.ofMillis(llm.getTimeoutMs()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Authorization", "Bearer " + llm.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body,
                            StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new AiCallException(determineStatus(status),
                        "AI 服务返回异常状态码 " + status + "："
                                + truncate(response.body()));
            }

            JsonNode root = MAPPER.readTree(response.body());
            String content = root.path("choices").path(0)
                    .path("message").path("content").asText(null);

            if (!StringUtils.hasText(content)) {
                throw new AiCallException(AiLogStatus.FAILED,
                        "AI 返回内容为空：" + truncate(response.body()));
            }

            return content;

        } catch (HttpTimeoutException e) {
            throw new AiCallException(AiLogStatus.TIMEOUT,
                    "AI 调用超时（超过 " + llm.getTimeoutMs() + " ms）", e);
        } catch (java.io.IOException e) {
            // 网络不通、DNS 失败、连接被拒等：属于可重试的临时故障
            throw new AiCallException(AiLogStatus.FAILED,
                    "AI 服务网络异常：" + e.getMessage(), e).retryable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiCallException(AiLogStatus.FAILED,
                    "AI 调用被中断", e).retryable();
        } catch (AiCallException e) {
            throw e;
        } catch (Exception e) {
            throw new AiCallException(AiLogStatus.FAILED,
                    "AI 调用异常：" + e.getMessage(), e);
        }
    }

    /**
     * 构造 OpenAI 兼容协议的请求体
     *
     * <p>关键点：
     * <ul>
     *   <li>用 {@code response_format=json_object} 提示模型输出纯 JSON，
     *       但<b>不依赖它</b>——解析时仍会剥离可能的 ```json 代码块；</li>
     *   <li>温度取低值，保证同一批输入的归纳结果稳定可复现。</li>
     * </ul>
     */
    private String buildRequestBody(AiProperties.Llm llm, String systemPrompt,
                                    String userPrompt) {
        try {
            var root = MAPPER.createObjectNode();
            root.put("model", llm.getModel());
            root.put("temperature", llm.getTemperature());
            root.put("stream", false);

            var format = root.putObject("response_format");
            format.put("type", "json_object");

            var messages = root.putArray("messages");
            var sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            var user = messages.addObject();
            user.put("role", "user");
            user.put("content", userPrompt);

            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new AiCallException(AiLogStatus.FAILED,
                    "构造 AI 请求失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从模型返回文本中解析出 JSON 对象
     *
     * <p><b>为什么要手写这段预处理</b>：
     * 大模型即便被要求输出纯 JSON，仍可能包裹 ```json 代码块，
     * 或在 JSON 前后加一句解释。这里做三件事：
     * 剥离代码块 → 截取第一个 { 到最后一个 } → 解析。
     * 解析失败统一抛异常，由上游降级，绝不让脏数据进入业务表。
     *
     * @param raw 模型返回原文
     * @return 解析后的 JSON 节点
     * @throws AiCallException 无法解析
     */
    private JsonNode parseJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new AiCallException(AiLogStatus.FAILED, "AI 返回内容为空");
        }
        try {
            String text = raw.trim();
            text = text.replaceAll("^```[a-zA-Z]*", "").replaceAll("```$", "").trim();

            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }

            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new AiCallException(AiLogStatus.FAILED,
                    "AI 返回内容无法解析为 JSON：" + truncate(raw), e);
        }
    }

    /**
     * 把 AI 返回的候选节点转换为内部建议对象
     *
     * <p><b>这里是"AI 不输出优先级"这条红线的最后一道闸</b>：
     * 即便模型在 JSON 里塞了 priority 字段，
     * 本方法也<b>不读取、不传递</b>，直接丢弃。
     */
    private AiCandidateSuggestion toSuggestion(JsonNode node) {

        List<Long> ids = new ArrayList<>();
        for (JsonNode idNode : node.path("feedbackIds")) {
            if (idNode.isNumber()) {
                ids.add(idNode.asLong());
            }
        }

        return AiCandidateSuggestion.builder()
                .title(text(node, "title", "（AI 未给出标题）"))
                .phenomenon(text(node, "phenomenon", ""))
                .userDemand(text(node, "userDemand", ""))
                .module(text(node, "module", "OTHER"))
                .confidence(clampConfidence(node.path("confidence").asDouble(0.5)))
                .reason(text(node, "reason", ""))
                .feedbackIds(ids)
                .build();
    }

    // ==================================================================
    // 提示词
    //
    // 设计原则：
    //   1. 强制 JSON 输出并给出 schema，禁止任何额外文字；
    //   2. 明确要求：信息不足时返回 INSUFFICIENT_EVIDENCE，禁止臆造；
    //   3. 相似度判断必须基于「问题现象」而非「关键词重合」；
    //   4. 每次输出必须给出所依据的 feedback_id 列表；
    //   5. 明确禁止输出优先级。
    // ==================================================================

    /**
     * 系统提示词 · 作用点 A（单条提取）
     */
    private static final String SYSTEM_EXTRACT = """
            你是一个企业报销软件的用户反馈分析助手，负责把客服录入的口语化反馈整理成结构化信息。

            严格要求：
            1. 只输出 JSON，不要输出任何解释、标题或代码块标记。
            2. 判断"是否属于同一件事"必须依据【问题现象】是否相同，而不是文字里是否出现了相同的词。
               例如"发票传不上去"与"发票上传成功但查不到"，虽然都含"上传"，但现象不同，是两个问题。
            3. 如果用户的话里出现并列（如"而且""另外""同时"），说明他在说多件事，必须拆成多个 item。
            4. 如果用户说得太笼统，无法判断具体哪里出问题（例如"有问题""系统很卡""希望能改进一下"），
               不要猜测，直接返回 {"insufficient": true}。
            5. 严禁输出优先级、严重程度或排期建议。

            输出 schema：
            {"insufficient": false, "items": [{"module": "模块代码", "phenomenon": "现象", "userDemand": "诉求", "signature": "现象签名"}]}
            """;

    /**
     * 系统提示词 · 作用点 B（聚类成卡片）
     */
    private static final String SYSTEM_CLUSTER = """
            你是一个企业报销软件的用户反馈分析助手，负责把一批用户反馈归纳成候选问题卡片。

            严格要求：
            1. 只输出 JSON，不要输出任何解释、标题或代码块标记。
            2. 只有当多条反馈描述的【问题现象】相同时，才归入同一张卡片；
               仅关键词相似但现象不同，必须拆成不同卡片。
            3. 同一条反馈里若写了多个不同的问题（典型标志是"而且/另外/同时"等并列，
               或前后两句话说的不是同一件事），不要把它们硬塞进一张卡片：
               允许把这条反馈的 id 分别写进多张候选卡片的 feedbackIds，
               每张卡片只归纳其中一个问题，并在 reason 里注明拆分依据。
            4. 一张卡片必须给出它所依据的 feedback_id 列表，不得凭空生成。
            5. 不得合并属于不同功能模块的反馈。
            6. 对于描述过于笼统、无法判断具体现象的反馈，把它的 id 放进
               insufficientFeedbackIds，不要为它生成卡片，更不要臆测。
            7. 严禁输出优先级、严重程度或排期建议；confidence 只表示你对"这些反馈是同一件事"的确信程度。

            输出 schema：
            {"candidates": [{"title": "标题", "phenomenon": "现象", "userDemand": "诉求",
                             "module": "模块代码", "confidence": 0.86, "reason": "归类理由",
                             "feedbackIds": [1,2,3]}],
             "insufficientFeedbackIds": [58]}
            """;

    /**
     * 系统提示词 · 作用点 C（起草需求）
     */
    private static final String SYSTEM_DRAFT = """
            你是一个企业报销软件的产品经理助手，负责为已确认的问题起草需求说明与验收条件。

            严格要求：
            1. 只输出 JSON，不要输出任何解释、标题或代码块标记。
            2. 只能基于给定的问题信息与用户原始反馈起草，不得编造未提及的功能或数据。
            3. 验收条件必须是可验证的具体条目，避免"体验更好""更流畅"这类无法衡量的描述。
            4. 严禁输出优先级、严重程度或排期建议。
            5. 草稿中如存在需要人工确认的点，请显式写在"待确认事项"小节里。

            输出 schema：
            {"draftContent": "Markdown 格式的需求说明",
             "acceptanceCriteria": "编号列出的验收条件"}
            """;

    /**
     * 构造作用点 A 的用户提示词
     */
    private String buildExtractUserPrompt(Feedback feedback) {
        return "模块代码候选：EXPENSE_FORM / INVOICE_UPLOAD / APPROVAL_PROGRESS / RETURN_MODIFY / OTHER\n"
                + "反馈ID: " + feedback.getId() + "\n"
                + "反馈原文: " + feedback.getContent() + "\n"
                + "请输出 JSON。";
    }

    /**
     * 构造作用点 B 的用户提示词
     *
     * <p>把所有反馈原文一次性给出，让模型在同一上下文中比较，
     * 比逐条询问再合并的结果一致性更好。
     */
    private String buildClusterUserPrompt(List<Feedback> batch) {

        StringBuilder sb = new StringBuilder();
        sb.append("以下是待归纳的用户反馈列表：\n");
        for (Feedback fb : batch) {
            sb.append("- id=").append(fb.getId())
                    .append(", module=").append(fb.getModule())
                    .append(", content=").append(fb.getContent())
                    .append('\n');
        }
        sb.append("\n请按要求输出 JSON。");
        return sb.toString();
    }

    /**
     * 构造作用点 C 的用户提示词
     */
    private String buildDraftUserPrompt(Issue issue,
                                        List<String> feedbackContents) {

        StringBuilder sb = new StringBuilder();
        sb.append("问题标题: ").append(issue.getTitle()).append('\n');
        sb.append("所属模块: ").append(issue.getModule()).append('\n');
        if (StringUtils.hasText(issue.getDescription())) {
            sb.append("问题描述: ").append(issue.getDescription()).append('\n');
        }
        if (feedbackContents != null && !feedbackContents.isEmpty()) {
            sb.append("关联的用户原始反馈:\n");
            int limit = Math.min(feedbackContents.size(), 10);
            for (int i = 0; i < limit; i++) {
                sb.append(i + 1).append(". ")
                        .append(feedbackContents.get(i)).append('\n');
            }
        }
        sb.append("\n请按要求输出 JSON。");
        return sb.toString();
    }

    // ==================================================================
    // 工具方法
    // ==================================================================

    /**
     * 规范化 base-url：去掉结尾多余的斜杠
     */
    private String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * 根据 HTTP 状态码判断日志状态
     *
     * <p>408 / 504 视为超时，其余非 2xx 视为普通失败。
     */
    private AiLogStatus determineStatus(int httpStatus) {
        return (httpStatus == 408 || httpStatus == 504)
                ? AiLogStatus.TIMEOUT
                : AiLogStatus.FAILED;
    }

    /**
     * 安全读取文本字段
     */
    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull()
                ? defaultValue : value.asText(defaultValue);
    }

    /**
     * 把置信度限制在 [0.05, 0.95]
     *
     * <p>即便模型给出一个荒唐的高分，也不允许它接近 1.0——
     * 因为再高的分数都不能代替人工确认，
     * 留出不确定的余量可以提醒 PM 这仍然是机器建议。
     */
    private BigDecimal clampConfidence(double value) {
        double safe = Math.max(0.05, Math.min(0.95, value));
        return BigDecimal.valueOf(safe).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 截断长文本，避免异常信息把日志表撑爆
     */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= MAX_ERROR_LEN
                ? clean : clean.substring(0, MAX_ERROR_LEN) + "…";
    }
}
