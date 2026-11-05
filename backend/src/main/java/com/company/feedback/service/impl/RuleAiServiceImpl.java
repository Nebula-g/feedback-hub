package com.company.feedback.service.impl;

import com.company.feedback.common.JsonUtil;
import com.company.feedback.dto.AiCandidateSuggestion;
import com.company.feedback.dto.AiClusterResult;
import com.company.feedback.dto.AiDraftResult;
import com.company.feedback.dto.AiExtractItem;
import com.company.feedback.entity.Feedback;
import com.company.feedback.entity.Issue;
import com.company.feedback.enums.AiProvider;
import com.company.feedback.service.AiService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 服务 · 本地规则实现【降级兜底 · 系统的地板】
 *
 * <p><b>为什么必须存在这个实现（对应降级设计要求）</b>：
 * 如果系统只有大模型一条路，那么一旦断网、Key 过期、供应商限流，
 * 「生成候选问题」这个核心动作就直接不可用，整个反馈处理链路被迫中断。
 * 有了本实现，最坏情况下 PM 依然能拿到一批可用的候选卡片，
 * 只是归纳质量差一些，<b>业务流程不断</b>。
 *
 * <p><b>它的工作原理（完全可解释，无黑盒）</b>：
 * <pre>
 *   反馈原文
 *     │
 *     ├─ ① 按并列连词切分（而且/并且/另外/还有/同时/以及）
 *     │     └─ 一条反馈可能拆出多个子问题（验收场景 2）
 *     │
 *     ├─ ② 每个子句匹配「现象规则表」
 *     │     ├─ 先在自己模块内部找（硬分组优先）
 *     │     └─ 找不到再去别的模块找（支持一条反馈跨模块拆分）
 *     │
 *     └─ ③ 按 「模块 + 现象签名」 归并成卡片
 *           └─ 同一个签名 = 同一个问题；不同签名绝不合在一起
 * </pre>
 *
 * <p><b>它刻意做不到的三件事（这正是它的诚实之处）</b>：
 * <ol>
 *   <li>不能理解同义改写：只认关键词，认不出"传不上去"和"无法提交"是一回事；</li>
 *   <li>不能跨模块合并：这是设计约束，不是缺陷；</li>
 *   <li>不会给出优先级：本实现与大模型实现一样，都不输出 priority。</li>
 * </ol>
 *
 * <p>这些局限会写进文档，并在答辩时如实说明——
 * 这正是"规则做地板、模型做增强"这一分层设计的意义。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@Service
public class RuleAiServiceImpl implements AiService {

    /** 固定模型标识：规则引擎版本号，不用 null 便于统计 */
    private static final String MODEL_NAME = "rule-engine-v1";

    /** 依据不足的签名：不参与归并，每张卡片只对应一条反馈 */
    private static final String SIG_INSUFFICIENT = "INSUFFICIENT_EVIDENCE";

    /** 未命中任何规则时的签名 */
    private static final String SIG_UNMATCHED = "UNMATCHED";

    /**
     * 跨模块兜底命中的置信度惩罚
     *
     * <p>反馈标注模块内没命中、靠其他模块关键词归进来的，
     * 可靠性明显低于本模块命中，统一扣减 0.15 并在归类理由里提示 PM。
     */
    private static final BigDecimal CROSS_MODULE_PENALTY = new BigDecimal("0.15");

    /**
     * 判定为"依据不足"的最大正文长度（按去标点后的字符数）
     *
     * <p>取值依据：演示数据中最短的有效反馈是"发票传不上去"（6 字），
     * 而典型的无效反馈是"系统很卡"（4 字）"希望能改进一下"（7 字）。
     * 取 6 作为分界，既能保住短而具体的反馈，又能拦住空泛表述。
     */
    private static final int INSUFFICIENT_MAX_LEN = 5;

    /**
     * 命中不了规则时，判定为"依据不足"的最大长度
     *
     * <p>一条较长的反馈如果连一个已知现象关键词都命中不了，
     * 说明它描述的是系统尚未归纳的新问题，此时应当保守处理。
     */
    private static final int UNMATCHED_INSUFFICIENT_MAX_LEN = 11;

    /** 并列连词：出现即说明用户在说两件不同的事 */
    private static final Pattern CLAUSE_SPLITTER = Pattern.compile(
            "而且|并且|另外|还有就是|还有|同时|以及");

    /**
     * 弱并列连接词「和 / 与」
     *
     * <p>不能直接加进 {@link #CLAUSE_SPLITTER}：中文里「和」大量用于连接名词短语
     * （"格式和大小""发票和报销单"），一刀切会把一句话切碎。
     * 因此只在<b>两侧都能各自命中已知问题现象、且现象不同</b>时才采用这种拆分——
     * 见 {@link #splitParallelClauses}。
     */
    private static final Pattern PARALLEL_JOINER = Pattern.compile("和|与");

    /** 需要裁掉的句读与空白字符 */
    private static final Pattern TRIM_CHARS = Pattern.compile(
            "^[，,。；;！!？?、\\s]+|[，,。；;！!？?、\\s]+$");

    // ==================================================================
    // 现象规则表
    //
    // 每条规则 = 一个「现象签名」+ 所属模块 + 归纳后的标题/现象/诉求
    //                              + 命中关键词
    //
    // ⚠️ 顺序敏感：先命中的规则优先。因此"具体到泛化"排列，
    //    避免"传不了"这类宽泛词把"格式错误"这类具体现象吃掉。
    // ==================================================================
    private static final List<PhenomenonRule> RULES = List.of(

            /* ---------------- 发票上传 INVOICE_UPLOAD ---------------- */
            rule("IU_OCR_WRONG", "INVOICE_UPLOAD",
                    "OCR 识别结果与票面不一致，导致报销金额错误",
                    "发票上传后 OCR 自动识别出的发票号码或金额与票面不符，用户需反复手工修改并重新提交。",
                    "提高识别准确率；识别结果允许人工修正并提供校验提示。",
                    "OCR", "识别不出", "识别出来", "识别不准", "识别错"),

            rule("IU_RECOGNIZE_SLOW", "INVOICE_UPLOAD",
                    "发票识别状态长时间停留在「识别中」，无进度反馈",
                    "发票上传成功后长时间停留在识别中状态，用户不知道还要等多久、是否会失败。",
                    "给出识别进度或预计等待时间，超时后主动通知用户。",
                    "识别中", "一直显示"),

            rule("IU_NOT_VISIBLE", "INVOICE_UPLOAD",
                    "发票上传成功但在报销单中查不到",
                    "系统提示上传成功，但报销单里看不到刚刚上传的发票，用户不敢重复提交。",
                    "上传成功后即时在报销单中可见，并提供明确的同步状态提示。",
                    "上传成功但", "看不到这张发票", "查不到", "看不到附件", "传上去了"),

            rule("IU_FORMAT_ERROR", "INVOICE_UPLOAD",
                    "发票格式校验误报，合法的 PDF 被拒绝",
                    "用户上传的确实是合规 PDF，系统却提示格式错误，且未说明支持的具体格式与限制。",
                    "修正格式校验逻辑，并在上传前清晰列出支持的类型、大小与页数限制。",
                    "格式错误", "格式不支持", "文件类型"),

            rule("IU_CANNOT_DELETE", "INVOICE_UPLOAD",
                    "已上传发票无法删除后重传",
                    "发票上传错了想删掉重新传，界面上找不到删除入口，只能整单作废重填。",
                    "提供单张发票的删除与替换入口。",
                    "删掉", "删除按钮", "删不了"),

            rule("IU_CANNOT_MOVE", "INVOICE_UPLOAD",
                    "发票上传错报销单后无法移动",
                    "发票传到了错误的报销单上，系统不提供移动到正确报销单的功能。",
                    "支持将已上传发票移动到其他报销单。",
                    "选错了", "移动"),

            rule("IU_DUPLICATE", "INVOICE_UPLOAD",
                    "重复上传同一张发票无任何提示",
                    "同一张发票被重复上传系统不拦截也不提示，导致重复报销风险。",
                    "上传时查重并提示，由用户确认是否仍要上传。",
                    "重复"),

            rule("IU_UPLOAD_FAIL", "INVOICE_UPLOAD",
                    "发票上传失败且缺少明确的失败原因",
                    "用户在多种网络与文件条件下上传发票均失败，系统只提示失败，不说明是格式、大小、网络还是服务端问题，也没有重试入口。",
                    "上传失败时展示可理解的具体原因，并支持一键重试、保留已填内容；批量上传时逐张反馈结果。",
                    "传不上去", "上传失败", "上传不成功", "传不了", "传不上", "都失败",
                    "总是失败", "还是失败", "中途失败", "只成功", "卡在", "超时",
                    "没反应", "网络错误"),

            /* ---------------- 报销单填写 EXPENSE_FORM ---------------- */
            rule("EF_PRINT_BROKEN", "EXPENSE_FORM",
                    "报销单详情页打印格式错乱",
                    "打印报销单详情页时版式错乱，无法作为纸质凭证归档。",
                    "提供适配 A4 的打印样式。",
                    "打印"),

            rule("EF_NO_TEMPLATE", "EXPENSE_FORM",
                    "不支持保存报销单模板，重复报销需反复填写",
                    "经常报销相同类型的费用，但没有模板功能，每次都要重新填写全部字段。",
                    "支持保存常用报销单为模板并一键套用。",
                    "模板"),

            rule("EF_COST_CENTER", "EXPENSE_FORM",
                    "填写时不知道该选哪个成本中心",
                    "成本中心列表缺少说明与检索，用户不知道自己应该选哪一个。",
                    "为成本中心补充说明、常用项排序与搜索能力。",
                    "成本中心"),

            rule("EF_ERROR_FLASH", "EXPENSE_FORM",
                    "表单校验错误提示一闪而过看不清",
                    "填写报错时红色提示很快消失，用户来不及看清是哪个字段、什么问题。",
                    "错误提示常驻直到修正，并在字段旁标注具体问题。",
                    "错误提示", "一闪而过", "看不清"),

            rule("EF_NO_SEARCH", "EXPENSE_FORM",
                    "报销单编号规则不清晰，历史单据难以检索",
                    "报销单编号缺少可读规则，用户无法凭编号定位或检索历史单据。",
                    "统一编号规则并在列表页提供按编号检索。",
                    "编号规则", "找之前"),

            rule("EF_TOO_MANY_FIELDS", "EXPENSE_FORM",
                    "差旅报销需填写字段过多",
                    "差旅类报销单需要填写的字段数量过多，填写成本高、易出错。",
                    "精简非必填字段，按行程自动带出可推导信息。",
                    "字段实在太多", "字段太多"),

            rule("EF_NO_AUTOFILL", "EXPENSE_FORM",
                    "发票识别金额未自动回填到报销单",
                    "上传发票后识别出的金额没有自动填入报销单对应字段，仍需手工录入。",
                    "识别完成后自动回填金额并允许用户校正。",
                    "自动填进", "自动填入"),

            rule("EF_NO_REUSE", "EXPENSE_FORM",
                    "不支持复制历史报销单",
                    "每月报销内容相似，但系统不支持复制上一月的报销单，只能逐项重填。",
                    "提供「复制上月/复制指定单据」功能。",
                    "复制上个月的", "复制上个月"),

            rule("EF_INPUT_LIMIT", "EXPENSE_FORM",
                    "报销事由字数上限过短",
                    "报销事由输入框的字数限制过短，无法把费用背景写清楚。",
                    "放宽字数上限并给出实时字数提示。",
                    "字数限制"),

            rule("EF_NO_AUTO_SUM", "EXPENSE_FORM",
                    "金额填写缺少自动合计",
                    "多行金额需要人工用计算器求和，效率低且容易算错。",
                    "提供实时合计与差额校验。",
                    "合计"),

            rule("EF_NO_EDIT_AFTER_SUBMIT", "EXPENSE_FORM",
                    "报销单提交后无法自助修改",
                    "提交后发现填错了，只能等审批人驳回才能改，拉长了处理周期。",
                    "在审批人尚未处理前允许提交人自助撤回修改。",
                    "提交之后", "只能等驳回"),

            rule("EF_NO_DRAFT", "EXPENSE_FORM",
                    "报销单填写内容未自动保存，刷新后丢失",
                    "填写过程中刷新或意外退出，已填内容全部丢失，需要从头再来。",
                    "提供草稿自动保存与断点续填。",
                    "刷新", "全没了", "继续编辑", "保存之后"),

            rule("EF_FIELD_HARD", "EXPENSE_FORM",
                    "报销单填写项缺少说明且难以定位",
                    "费用类型等下拉项数量多、缺少说明，用户不知道哪些能报、该选哪个。",
                    "补充报销范围说明、优化选项排序并提供搜索。",
                    "下拉菜单", "不知道哪些", "缺少说明", "不知道该选"),

            /* ---------------- 审批进度 APPROVAL_PROGRESS ---------------- */
            rule("AP_NO_WITHDRAW", "APPROVAL_PROGRESS",
                    "缺少撤回已提交审批的功能",
                    "提交后想撤回重新修改，但系统没有撤回入口，只能等驳回。",
                    "在首个审批节点处理前允许提交人自助撤回。",
                    "撤回"),

            rule("AP_MOBILE", "APPROVAL_PROGRESS",
                    "审批人无法在移动端处理审批",
                    "审批必须登录电脑端才能处理，导致外出时审批长期积压。",
                    "补齐移动端审批能力。",
                    "手机上", "移动端"),

            rule("AP_NO_URGE", "APPROVAL_PROGRESS",
                    "缺少催办入口，单据积压只能干等",
                    "单据长时间无人处理，用户找不到催办按钮，也没有任何升级机制。",
                    "提供催办入口与超时自动升级提醒。",
                    "催办"),

            rule("AP_NO_ETA", "APPROVAL_PROGRESS",
                    "审批流程缺少预计完成时间",
                    "用户看不到预计什么时候能审批完，无法安排后续工作。",
                    "基于历史时长给出预计完成时间。",
                    "预计完成时间", "预计"),

            rule("AP_NO_REFRESH", "APPROVAL_PROGRESS",
                    "审批进度页面不会自动刷新",
                    "进度页需要手动刷新才能看到最新状态，用户只能反复刷新确认。",
                    "增加自动刷新或推送更新。",
                    "自动刷新", "手动刷新"),

            rule("AP_FLOW_UNCLEAR", "APPROVAL_PROGRESS",
                    "审批流程图不可读，无法定位卡点",
                    "流程图信息密度低、缺少当前节点高亮，用户看不出单据卡在哪一步。",
                    "优化流程图展示并高亮当前节点。",
                    "流程图", "看不懂", "不知道具体"),

            rule("AP_HISTORY_INCOMPLETE", "APPROVAL_PROGRESS",
                    "审批历史只能看到最终结果，缺少各环节意见",
                    "审批记录只有通过与驳回的结论，看不到每位审批人的具体意见。",
                    "完整展示各环节处理人、时间与意见。",
                    "历史记录", "每个人的意见"),

            rule("AP_STUCK_APPROVER", "APPROVAL_PROGRESS",
                    "审批人离职或变更导致单据长期卡单",
                    "原审批人离职后单据无人接管，长期停留在同一节点。",
                    "提供审批人变更检测与自动转办机制。",
                    "离职", "卡在那里"),

            rule("AP_NO_CURRENT_OWNER", "APPROVAL_PROGRESS",
                    "审批进度看不到当前处理人",
                    "提交后无法得知单据当前在谁手里，也无法联系到责任人。",
                    "在详情页展示当前节点、处理人与停留时长。",
                    "卡在谁", "当前处理人", "不知道.*谁"),

            rule("AP_TOO_SLOW", "APPROVAL_PROGRESS",
                    "审批周期过长，大额单据尤其明显",
                    "审批层级多、周期长，大额单据动辄需要数周才能批完。",
                    "按金额分级设置审批层级与时限。",
                    "特别慢", "太慢", "一个月", "层级太多"),

            rule("AP_NO_NOTIFY", "APPROVAL_PROGRESS",
                    "审批结果与异常缺少通知提醒",
                    "审批通过、驳回或超时均未主动通知，用户只能自行反复查看。",
                    "补齐审批结果、驳回与超时的主动通知。",
                    "没有通知", "没收到", "提醒", "预警"),

            /* ---------------- 退回修改 RETURN_MODIFY ---------------- */
            rule("RM_NO_COMMUNICATE", "RETURN_MODIFY",
                    "缺少与审批人沟通的入口",
                    "被退回后不知道该问谁，也没有在线沟通渠道，只能线下反复确认。",
                    "提供针对退回意见的追问与沟通入口。",
                    "沟通", "问审批人"),

            rule("RM_NO_LINK", "RETURN_MODIFY",
                    "退回通知缺少直达链接",
                    "退回通知只说被退回，没有跳转到具体单据的链接，用户找不到是哪一张。",
                    "通知中附带直达单据详情的链接。",
                    "链接", "找不到是哪张"),

            rule("RM_REASON_INCONSISTENT", "RETURN_MODIFY",
                    "多次退回原因不一致，修改无从下手",
                    "同一张单据被多次退回，每次给出的原因不同，用户无法确定真正的问题。",
                    "汇总历史退回意见并明确最终待改项。",
                    "原因还都不一样", "不一致"),

            rule("RM_REDO_ALL", "RETURN_MODIFY",
                    "修改后重新提交需重走完整流程",
                    "按意见修改后重新提交，审批要从第一个节点重新开始，处理周期被拉长。",
                    "小修改支持回到原节点继续审批。",
                    "重新走", "重新提交"),

            rule("RM_REMARK_UNREADABLE", "RETURN_MODIFY",
                    "退回意见可读性差",
                    "退回意见写在备注里、字号过小，用户看不清具体内容。",
                    "独立展示退回意见并提升可读性。",
                    "备注", "字太小"),

            rule("RM_NO_REASON", "RETURN_MODIFY",
                    "退回原因展示不明确，用户不知道要改哪里",
                    "单据被退回后只显示「已退回」，看不到具体退回原因与待修改项。",
                    "退回时必须填写结构化原因，并在详情页显著展示。",
                    "退回原因", "找不到原因", "不知道要改", "请修改")
    );

    /** 按签名索引规则，便于归并时反查中文文案 */
    private static final Map<String, PhenomenonRule> RULE_MAP =
            RULES.stream().collect(Collectors.toMap(PhenomenonRule::getSignature, r -> r));

    // ==================================================================
    // 接口实现
    // ==================================================================

    /**
     * {@inheritDoc}
     *
     * <p>规则实现恒返回 RULE。
     */
    @Override
    public String provider() {
        return AiProvider.RULE.getCode();
    }

    /**
     * {@inheritDoc}
     *
     * <p>规则实现固定返回引擎版本号。
     */
    @Override
    public String model() {
        return MODEL_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * <p>规则实现不依赖网络与密钥，<b>永远可用</b>——这正是它能当"地板"的原因。
     */
    @Override
    public boolean available() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>拆分规则</b>：正文中出现"而且 / 并且 / 另外 / 还有 / 同时 / 以及"时，
     * 拆成多个子句分别归类；否则当作一个整体。
     * 另有一条带守卫的补充规则：出现「和 / 与」且两侧都能各自命中不同现象时
     * 也算两个问题（见 {@link #splitParallelClauses}）。
     */
    @Override
    public List<AiExtractItem> extract(Feedback feedback) {

        List<AiExtractItem> items = new ArrayList<>();

        if (feedback == null || !StringUtils.hasText(feedback.getContent())) {
            // 空内容没有任何可归纳的依据，直接判为依据不足
            items.add(AiExtractItem.builder()
                    .feedbackId(feedback == null ? null : feedback.getId())
                    .module(feedback == null ? null : feedback.getModule())
                    .signature(SIG_INSUFFICIENT)
                    .phenomenon("反馈内容为空")
                    .userDemand("需客服补充反馈原文")
                    .build());
            return items;
        }

        for (String clause : splitClauses(feedback.getContent())) {
            items.add(classifyClause(feedback, clause));
        }

        if (items.isEmpty()) {
            // 理论上走不到：splitClauses 至少返回一个子句
            items.add(classifyClause(feedback, feedback.getContent()));
        }

        // 强连词没切出多个子句时，再试一次「和 / 与」并列：
        // 典型语料是"上传失败和看不到退回原因"——两件事被一个"和"连在一起，
        // 用强连词切不开，但确实是两个问题。
        if (items.size() == 1) {
            List<AiExtractItem> parallel = splitParallelClauses(feedback);
            if (parallel != null) {
                return parallel;
            }
        }

        return items;
    }

    /**
     * 按「和 / 与」再尝试一次并列拆分
     *
     * <p><b>为什么需要守卫</b>：「和」在中文里既能连接两个问题，
     * 也能连接两个名词（"格式和大小""发票和报销单"）。
     * 因此这里加两条硬条件，全部满足才认为是"两个问题"：
     * <ol>
     *   <li>每一侧的文本都非空；</li>
     *   <li>每一侧都能单独命中已知现象规则（没有一侧落在
     *       「依据不足 / 未匹配」里），且两侧的现象签名不完全相同。</li>
     * </ol>
     * 只要有一条不满足就返回 null，退回原整体归类——
     * 宁可少拆，也不要把一句话拆成两张无意义的卡片。
     *
     * @param feedback 原始反馈
     * @return 拆出的多个提取结果；不满足拆分条件时返回 null
     */
    private List<AiExtractItem> splitParallelClauses(Feedback feedback) {

        String[] sides = PARALLEL_JOINER.split(feedback.getContent());
        if (sides.length < 2) {
            return null;
        }

        List<AiExtractItem> result = new ArrayList<>();
        Set<String> signatures = new HashSet<>();

        for (String raw : sides) {
            String clause = TRIM_CHARS.matcher(raw).replaceAll("");
            if (!StringUtils.hasText(clause)) {
                return null;
            }

            AiExtractItem item = classifyClause(feedback, clause);
            if (SIG_INSUFFICIENT.equals(item.getSignature())
                    || SIG_UNMATCHED.equals(item.getSignature())) {
                // 有一侧不是已知问题：这里只是名词并列，或语义不完整
                return null;
            }

            signatures.add(item.getModule() + "|" + item.getSignature());
            result.add(item);
        }

        // 两侧命中同一个现象，说明还是同一件事（"上传失败和上传报错"），不拆
        return signatures.size() >= 2 ? result : null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>归并规则（硬分组）：
     * <ul>
     *   <li>命中已知规则的：按「模块 + 签名」归并，同一签名视为同一问题</li>
     *   <li>依据不足的：一条反馈一张卡片，<b>不做任何归并</b>，
     *       避免把"说不清楚"的反馈硬凑成一类</li>
     * </ul>
     */
    @Override
    public AiClusterResult cluster(List<Feedback> feedbacks) {

        List<AiCandidateSuggestion> result = new ArrayList<>();

        if (feedbacks == null || feedbacks.isEmpty()) {
            return AiClusterResult.builder()
                    .candidates(result)
                    .rawOutput("[]")
                    .build();
        }

        // key → 同一张候选卡片下的所有提取结果
        Map<String, List<AiExtractItem>> groups = new LinkedHashMap<>();
        for (Feedback feedback : feedbacks) {
            for (AiExtractItem item : extract(feedback)) {
                String key = buildGroupKey(item);
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            }
        }

        for (List<AiExtractItem> items : groups.values()) {
            result.add(buildSuggestion(items));
        }

        // 置信度高的排前面：让 PM 先处理最有把握的卡片
        result.sort(Comparator.comparing(
                AiCandidateSuggestion::getConfidence,
                Comparator.reverseOrder()));

        log.debug("规则聚类完成, 输入{}条反馈, 输出{}张候选卡片",
                feedbacks.size(), result.size());

        // 规则实现没有"模型返回原文"，这里把等价的结构化结果作为原文保存，
        // 保证日志表的 output_raw 字段永远非空、可复盘。
        return AiClusterResult.builder()
                .candidates(result)
                .rawOutput(JsonUtil.toJson(result))
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>规则实现无法真正"理解"需求，只能按固定模板把已有信息组织成草稿，
     * 并明确标注"待人工补充"，绝不伪造具体实现方案。
     */
    @Override
    public AiDraftResult draft(Issue issue, List<String> feedbackContents) {

        if (issue == null) {
            return AiDraftResult.builder()
                    .draftContent("（问题不存在，无法起草）")
                    .acceptanceCriteria("")
                    .build();
        }

        int feedbackCount = feedbackContents == null ? 0 : feedbackContents.size();

        StringBuilder draft = new StringBuilder();
        draft.append("## 问题概述\n")
                .append(issue.getTitle()).append("\n\n")
                .append("【所属模块】").append(issue.getModule()).append("\n")
                .append("【关联反馈】").append(feedbackCount).append(" 条\n\n");

        if (feedbackCount > 0) {
            draft.append("## 用户原始反馈（摘录）\n");
            int limit = Math.min(feedbackCount, 5);
            for (int i = 0; i < limit; i++) {
                draft.append(i + 1).append(". ")
                        .append(abbreviate(feedbackContents.get(i), 60)).append("\n");
            }
            draft.append("\n");
        }

        draft.append("## 需求说明（草稿 · 需人工补充）\n")
                .append("1. 复现并确认问题现象：").append(issue.getTitle()).append("\n")
                .append("2. 明确期望行为与判定标准\n")
                .append("3. 补充异常提示文案与兜底方案\n\n")
                .append("> 本节为规则引擎按模板生成的初稿，" +
                        "仅做结构占位，不含任何推断出的实现方案，需由产品经理补充。");

        String criteria = "1. 可按上述关联反馈的描述稳定复现问题\n"
                + "2. 修复后该现象不再出现，且不影响其他模块\n"
                + "3. 失败或异常场景下给出可理解的提示文案\n"
                + "4. 补充必要日志，便于后续同类问题定位\n"
                + "5. 由产品经理与客服共同验收通过";

        return AiDraftResult.builder()
                .draftContent(draft.toString())
                .acceptanceCriteria(criteria)
                .build();
    }

    // ==================================================================
    // 私有方法
    // ==================================================================

    /**
     * 按并列连词把一段反馈拆成若干子句
     *
     * <p><b>为什么只在连词处切分，而不按逗号切分</b>：
     * 中文口语反馈里逗号经常只是停顿，例如
     * "发票死活传不上去，换了三个网络都不行，也不知道哪里出问题"
     * 三句话说的是同一件事，按逗号切开会错误地拆成三个问题。
     * 只有在出现明确并列连词时，才说明用户在列举不同的问题。
     *
     * @param content 反馈原文
     * @return 子句列表，至少包含 1 个元素
     */
    private List<String> splitClauses(String content) {

        List<String> clauses = new ArrayList<>();

        for (String raw : CLAUSE_SPLITTER.split(content)) {
            String clause = TRIM_CHARS.matcher(raw).replaceAll("");
            if (StringUtils.hasText(clause)) {
                clauses.add(clause);
            }
        }

        // 连词出现在句首等极端情况下可能切空，兜底用原文
        if (clauses.isEmpty()) {
            clauses.add(TRIM_CHARS.matcher(content).replaceAll(""));
        }

        return clauses;
    }

    /**
     * 对单个子句做归类
     *
     * <p><b>两轮匹配</b>：
     * <ol>
     *   <li>先在该反馈<b>所属模块</b>的规则里找——保证"自己模块优先"</li>
     *   <li>找不到再去其他模块的规则里找——这样才能把
     *       "上传发票总是失败，而且报销单被退回后也找不到原因"
     *       这样的反馈拆成两个不同模块的问题</li>
     * </ol>
     *
     * @param feedback 来源反馈
     * @param clause   子句
     * @return 提取结果
     */
    private AiExtractItem classifyClause(Feedback feedback, String clause) {

        String ownModule = feedback.getModule();

        // 第一轮：只在本模块规则中匹配
        PhenomenonRule ownHit = matchRule(clause, ownModule, true);

        // 第二轮：本模块没命中，再去其他模块找（支持一条反馈拆出跨模块的问题）
        PhenomenonRule hit = ownHit != null
                ? ownHit : matchRule(clause, ownModule, false);

        if (hit != null) {
            return AiExtractItem.builder()
                    .feedbackId(feedback.getId())
                    .module(hit.getModule())
                    .signature(hit.getSignature())
                    .phenomenon(hit.getPhenomenon())
                    .userDemand(hit.getUserDemand())
                    // 本模块没命中、靠其他模块兜底命中的：可靠性更低，需要提示 PM
                    .crossModule(ownHit == null)
                    .build();
        }

        // 没有任何关键词命中：内容过短视为依据不足，否则交给人工判断
        boolean insufficient = plainLength(clause) <= INSUFFICIENT_MAX_LEN
                || plainLength(clause) <= UNMATCHED_INSUFFICIENT_MAX_LEN;

        return AiExtractItem.builder()
                .feedbackId(feedback.getId())
                .module(ownModule)
                .signature(insufficient ? SIG_INSUFFICIENT : SIG_UNMATCHED)
                .phenomenon(insufficient
                        ? "依据不足：原文「" + clause + "」缺少可定位的现象描述"
                        : "未能匹配已知现象，需人工判断：「" + clause + "」")
                .userDemand(insufficient
                        ? "需客服补充：具体页面、操作步骤、期望结果与实际结果"
                        : "需产品经理人工归类并补充需求")
                .build();
    }

    /**
     * 在规则表中查找第一个命中的规则
     *
     * @param clause      子句
     * @param module      参照模块
     * @param sameModule  为 true 时只匹配该模块的规则，为 false 时只匹配其他模块
     * @return 命中的规则，未命中返回 null
     */
    private PhenomenonRule matchRule(String clause, String module,
                                     boolean sameModule) {
        for (PhenomenonRule rule : RULES) {
            boolean belongs = rule.getModule().equalsIgnoreCase(
                    module == null ? "" : module);
            if (belongs != sameModule) {
                continue;
            }
            for (String keyword : rule.getKeywords()) {
                // 支持简单正则，例如 "不知道.*谁"
                if ( Pattern.compile(keyword).matcher(clause).find()) {
                    return rule;
                }
            }
        }
        return null;
    }

    /**
     * 计算分组 key
     *
     * <p>普通现象：模块 + 签名，同签名即同问题。
     * 依据不足/未命中：<b>一条反馈一个 key</b>，绝不归并——
 * 把说不清楚的反馈硬凑成一类，比不归类更危险。
     *
     * @param item 提取结果
     * @return 分组 key
     */
    private String buildGroupKey(AiExtractItem item) {
        if (SIG_INSUFFICIENT.equals(item.getSignature())
                || SIG_UNMATCHED.equals(item.getSignature())) {
            return item.getSignature() + "#" + item.getFeedbackId();
        }
        return item.getModule() + "|" + item.getSignature();
    }

    /**
     * 把一组同签名的提取结果组装成一张候选卡片
     *
     * @param items 同一分组下的提取结果
     * @return 候选卡片建议
     */
    private AiCandidateSuggestion buildSuggestion(List<AiExtractItem> items) {

        AiExtractItem first = items.get(0);
        PhenomenonRule rule = RULE_MAP.get(first.getSignature());

        List<Long> ids = items.stream()
                .map(AiExtractItem::getFeedbackId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // 是否存在靠"跨模块兜底"命中的反馈：这类归并可靠性更低
        boolean hasCrossModule = items.stream()
                .anyMatch(AiExtractItem::isCrossModule);

        String title;
        String phenomenon;
        String demand;
        String reason;
        BigDecimal confidence = calcConfidence(first.getSignature(), ids.size());

        if (rule != null) {
            title = rule.getTitle();
            phenomenon = rule.getPhenomenon();
            demand = rule.getUserDemand();
            reason = "共 " + ids.size() + " 条反馈均描述「" + rule.getPhenomenon()
                    + "」，判定为同一现象（规则签名 " + rule.getSignature() + "）。"
                    + "依据反馈：" + formatIds(ids) + "。"
                    + "以上为文本相似度归纳，是否成立需产品经理确认。";

            // 跨模块兜底命中：降置信度并明确提示，让 PM 优先用拆分/驳回复核这类卡片，
            // 而不是被统一的关键词文案误导（典型误匹配：「网络错误」在多个模块都常见）
            if (hasCrossModule) {
                confidence = confidence.subtract(CROSS_MODULE_PENALTY)
                        .max(BigDecimal.valueOf(0.05));
                reason = reason + "⚠ 注意：有反馈在其标注模块内未命中规则，"
                        + "是靠其他模块的关键词兜底归进来的，"
                        + "存在跨模块误匹配的可能，请核对其真实所属模块"
                        + "（必要时用「拆分」修正归属）。";
            }
        } else {
            // 未命中或依据不足：如实说明，不做任何推断
            title = abbreviate(first.getPhenomenon(), 40);
            phenomenon = first.getPhenomenon();
            demand = first.getUserDemand();
            reason = "反馈 " + formatIds(ids) + " 未匹配到已知现象，"
                    + "缺少足够的归类依据，未做自动合并，需人工判断。";
        }

        return AiCandidateSuggestion.builder()
                .title(title)
                .phenomenon(phenomenon)
                .userDemand(demand)
                .module(first.getModule())
                .confidence(confidence)
                .reason(reason)
                .feedbackIds(ids)
                .build();
    }

    /**
     * 计算置信度
     *
     * <p><b>计算口径完全公开、可复算</b>（不使用任何黑盒打分）：
     * <pre>
     *   base             = 0.50
     *   + 关键词命中规则  = 0.15
     *   + 关联反馈增量    = min(0.30, (条数 - 1) × 0.10)
     *   - 依据不足        = 0.25
     *   - 未命中规则      = 0.10
     *   - 跨模块兜底命中  = 0.15（在 buildSuggestion 中扣减）
     *   最终截断到 [0.05, 0.95]
     * </pre>
     *
     * <p><b>置信度不触发任何自动动作</b>：
     * 即便为 0.95，也仍需 PM 手动确认才生成正式问题。
     * 它唯一的用途是列表排序与提示 PM 优先看哪些卡片。
     *
     * @param signature 现象签名
     * @param count     关联反馈条数
     * @return 置信度
     */
    private BigDecimal calcConfidence(String signature, int count) {

        double value = 0.50;

        if (RULE_MAP.containsKey(signature)) {
            value += 0.15;
        }
        value += Math.min(0.30, Math.max(0, count - 1) * 0.10);

        if (SIG_INSUFFICIENT.equals(signature)) {
            value -= 0.25;
        } else if (SIG_UNMATCHED.equals(signature)) {
            value -= 0.10;
        }

        value = Math.max(0.05, Math.min(0.95, value));

        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 去掉标点与空白后的字符数
     *
     * <p>用于判断反馈是否"说得太少"。
     * 先去掉标点，避免"有问题，找技术"这种
     * 靠标点凑长度的表述被误判为信息充足。
     *
     * @param text 原文
     * @return 有效字符数
     */
    private int plainLength(String text) {
        if (text == null) {
            return 0;
        }
        return text.replaceAll("[，,。；;！!？?、\\s]", "").length();
    }

    /**
     * 文本截断（超出部分用省略号）
     *
     * @param text  原文
     * @param maxLen 最大长度
     * @return 截断后的文本
     */
    private String abbreviate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= maxLen ? clean : clean.substring(0, maxLen) + "…";
    }

    /**
     * 把反馈 ID 列表格式化成 "#1 #2 #3"
     *
     * @param ids 反馈 ID 列表
     * @return 格式化文本
     */
    private String formatIds(List<Long> ids) {
        return ids.stream().map(id -> "#" + id).collect(Collectors.joining(" "));
    }

    /**
     * 规则构造器（简化静态表书写）
     */
    private static PhenomenonRule rule(String signature, String module,
                                       String title, String phenomenon,
                                       String userDemand, String... keywords) {
        return new PhenomenonRule(signature, module, title, phenomenon,
                userDemand, List.of(keywords));
    }

    /**
     * 现象规则（不可变）
     *
     * <p>把"一类用户抱怨"固化成一条数据：
     * 命中关键词 → 归到这个签名 → 用这套文案生成卡片。
     * 改文案不需要改代码逻辑，符合"规则与执行分离"。
     */
    @Getter
    @AllArgsConstructor
    private static class PhenomenonRule {

        /** 现象签名：聚类的唯一 key */
        private final String signature;

        /** 所属功能模块 */
        private final String module;

        /** 归纳后的卡片标题 */
        private final String title;

        /** 归纳后的现象描述 */
        private final String phenomenon;

        /** 归纳后的用户诉求 */
        private final String userDemand;

        /** 命中关键词（支持简单正则） */
        private final List<String> keywords;
    }
}
