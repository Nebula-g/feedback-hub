package com.company.feedback.common;

import com.company.feedback.enums.CandidateStatus;
import com.company.feedback.enums.FeedbackModule;
import com.company.feedback.enums.FeedbackStatus;
import com.company.feedback.enums.RoleEnum;
import com.company.feedback.enums.TaskPriority;
import com.company.feedback.enums.TaskStatus;

/**
 * 枚举代码 → 中文显示名的转换工具
 *
 * <p><b>为什么要单独抽一个工具类</b>：
 * 前端拿到的是 {@code CODE} 形式的状态值（便于做判断和国际化），
 * 但界面上要显示「待验证」这样的中文。
 * 如果让每个 VO 的组装处各写一遍 switch，
 * 一旦某个状态的中文名改了，就要满项目找。
 * 集中在这里，改一处即可全局生效。
 *
 * <p><b>容错策略</b>：传入 null 或未知代码时返回 null，
 * 而不是抛异常。状态名只是展示用的辅助信息，
 * 不该因为它缺失就让整个接口失败。
 *
 * @author 反馈改进助手项目组
 */
public final class StatusLabelUtil {

    /**
     * 私有构造：工具类不允许实例化
     */
    private StatusLabelUtil() {
    }

    /**
     * 任务状态代码转中文
     *
     * @param code 状态代码，如 IN_PROGRESS
     * @return 中文名，如「开发中」；无法识别时返回 null
     */
    public static String taskStatusLabel(String code) {
        TaskStatus status = TaskStatus.of(code);
        return status == null ? null : status.getLabel();
    }

    /**
     * 反馈状态代码转中文
     *
     * @param code 状态代码，如 LINKED
     * @return 中文名；无法识别时返回 null
     */
    public static String feedbackStatusLabel(String code) {
        FeedbackStatus status = FeedbackStatus.of(code);
        return status == null ? null : status.getLabel();
    }

    /**
     * AI 候选卡片状态代码转中文
     *
     * @param code 状态代码，如 PENDING_CONFIRM
     * @return 中文名；无法识别时返回 null
     */
    public static String candidateStatusLabel(String code) {
        CandidateStatus status = CandidateStatus.of(code);
        return status == null ? null : status.getLabel();
    }

    /**
     * 优先级代码转中文
     *
     * @param code 优先级代码，如 P0
     * @return 中文名；无法识别时返回 null
     */
    public static String priorityLabel(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        // TaskPriority.of 对未知代码会回落到 P2，
        // 这里需要区分"真的是 P2"和"压根不是合法值"，
        // 因此先用 isValid 判定，避免把非法值显示成 P2 误导使用者。
        return TaskPriority.isValid(code) ? TaskPriority.of(code).getLabel() : null;
    }

    /**
     * 功能模块代码转中文
     *
     * @param code 模块代码，如 INVOICE_UPLOAD
     * @return 中文名；无法识别时返回 null
     */
    public static String moduleLabel(String code) {
        FeedbackModule module = FeedbackModule.of(code);
        return module == null ? null : module.getLabel();
    }

    /**
     * 角色代码转中文
     *
     * @param code 角色代码，如 PM
     * @return 中文名；无法识别时返回 null
     */
    public static String roleLabel(String code) {
        RoleEnum role = RoleEnum.of(code);
        return role == null ? null : role.getLabel();
    }
}
