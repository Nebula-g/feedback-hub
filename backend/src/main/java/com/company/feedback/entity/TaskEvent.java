package com.company.feedback.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务事件实体【核心业务对象 · 时间线】
 *
 * <p>对应表：task_event
 *
 * <p><b>用途</b>：记录任务生命周期中每一次状态变更与关键动作，
 * 形成"谁在什么时候、以什么身份、把任务从什么状态改成了什么状态"的完整时间线。
 *
 * <p><b>为什么 operator_role 要存快照而不是实时关联查询</b>：
 * 如果只存 operator_id，查询时再去 sys_user 表关联当前角色，
 * 那么一旦某人从 DEV 转成 PM，他历史上所有的开发操作记录
 * 都会被显示成"产品经理操作的"，历史就失真了。
 * 存快照保证的是：历史记录反映的是<b>当时的真实情况</b>。
 *
 * <p><b>本表只追加，不修改、不删除</b>：
 * 与 ai_generation_log 同理，属于证据类数据。
 * 任何状态的变更都体现在新增一条事件上，而非修改已有事件。
 *
 * <p><b>为什么本实体不继承 BaseEntity</b>：
 * 事件一旦写入就永不修改，"更新时间"这一列在本表没有意义。
 * 强行继承 BaseEntity 会让 MyBatis-Plus 在 UPDATE 时带上 update_time，
 * 而表里并没有这一列，直接导致 SQL 语法错误。
 * 因此这里只声明 id 与 create_time，与表结构严格对齐。
 *
 * @author 反馈改进助手项目组
 */
@Data
@TableName("task_event")
public class TaskEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID（数据库自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间（插入时自动填充）
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 任务 ID
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 原状态：任务创建时为 null
     */
    @TableField("from_status")
    private String fromStatus;

    /**
     * 新状态
     */
    @TableField("to_status")
    private String toStatus;

    /**
     * 操作人 ID
     */
    @TableField("operator_id")
    private Long operatorId;

    /**
     * 操作人角色快照：CS / PM / DEV / ADMIN
     */
    @TableField("operator_role")
    private String operatorRole;

    /**
     * 处理说明：打回、关闭时必填
     */
    @TableField("remark")
    private String remark;
}
