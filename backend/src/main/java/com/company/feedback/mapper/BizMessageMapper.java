package com.company.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.feedback.entity.BizMessage;
import com.company.feedback.vo.MessageGroupVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 业务对象评论 Mapper
 *
 * @author 反馈改进助手项目组
 */
public interface BizMessageMapper extends BaseMapper<BizMessage> {

    /**
     * 未读评论按业务对象聚合
     *
     * <p>为什么用注解 SQL 而不是 Wrapper：
     * GROUP BY + COUNT + MAX 的聚合无法用 LambdaQueryWrapper 表达，
     * 手写 SQL 语义最直白。
     *
     * <p><b>「与我有关」的判定</b>：只有我是该对象的录入人（反馈）
     * 或负责人/创建人（任务）时才出现在我的消息面板里。
     * 缺少这一层过滤会导致每个用户都能看到别人反馈下的评论——
     * 既干扰阅读，也让未读数永远清不掉。
     *
     * <p>已知边界：单纯"参与过讨论但非对象负责人"的用户
     * 不会收到后续回复提醒（MVP 取舍，二期做参与人表或 @提及）。
     *
     * <p>sender_id != #{userId}：自己发的评论永远不算"别人找我"。
     *
     * @param userId 当前用户 ID
     * @return 聚合结果（bizType/bizId/count/lastTime）
     */
    @Select("SELECT m.biz_type AS bizType, m.biz_id AS bizId, "
            + "COUNT(*) AS count, MAX(m.create_time) AS lastTime "
            + "FROM biz_message m "
            + "WHERE m.is_read = 0 AND m.sender_id != #{userId} "
            + "AND ("
            + "  (m.biz_type = 'FEEDBACK' AND EXISTS ("
            + "     SELECT 1 FROM feedback f WHERE f.id = m.biz_id AND f.created_by = #{userId})) "
            + "  OR "
            + "  (m.biz_type = 'TASK' AND EXISTS ("
            + "     SELECT 1 FROM improvement_task t WHERE t.id = m.biz_id "
            + "       AND (t.assignee_id = #{userId} OR t.created_by = #{userId})))"
            + ") "
            + "GROUP BY m.biz_type, m.biz_id "
            + "ORDER BY lastTime DESC LIMIT 20")
    List<MessageGroupVO> selectUnreadGroups(@Param("userId") Long userId);

    /**
     * 取某业务对象下最新一条评论内容
     *
     * @param bizType 业务对象类型
     * @param bizId   业务对象 ID
     * @return 最新一条评论内容，无评论返回 null
     */
    @Select("SELECT content FROM biz_message "
            + "WHERE biz_type = #{bizType} AND biz_id = #{bizId} "
            + "ORDER BY id DESC LIMIT 1")
    String selectLastContent(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /**
     * 将某业务对象下、除指定人之外的未读评论整批置为已读
     *
     * <p>语义：「我看过这个对象的评论区」——
     * 自己发的评论不参与统计，因此排除 sender。
     *
     * @param bizType 业务对象类型
     * @param bizId   业务对象 ID
     * @param userId  当前用户（查看人）ID
     * @return 更新行数
     */
    @Update("UPDATE biz_message SET is_read = 1 "
            + "WHERE biz_type = #{bizType} AND biz_id = #{bizId} "
            + "AND sender_id != #{userId} AND is_read = 0")
    int markBizRead(@Param("bizType") String bizType,
                    @Param("bizId") Long bizId,
                    @Param("userId") Long userId);
}
