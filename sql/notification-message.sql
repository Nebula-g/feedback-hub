-- ============================================================================
-- 企业报销软件用户反馈与功能改进助手 —— 通知与消息建表脚本（增量）
-- 数据库：MySQL 8.0
-- 版本：v1.1
-- 日期：2026-11-04
--
-- 设计要点：
--   1. notification 为系统事件通知（机器触发、单人接收），收件人由业务逻辑唯一确定
--   2. biz_message 为围绕业务对象（反馈/任务）的评论留言，不设点对点收件人，
--      已读以「业务对象」为单位标记——看过该对象评论区即视为已读
--   3. 两表均不做逻辑删除：通知/消息属于流水数据，保留完整历史
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `feedback_hub`;

-- ----------------------------------------------------------------------------
-- 1. notification —— 站内通知表【系统事件 · 单人接收】
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     BIGINT       NOT NULL                COMMENT '接收人ID（sys_user.id）',
  `type`        VARCHAR(30)  NOT NULL                COMMENT '类型：TASK_ASSIGNED/TASK_STATUS/FEEDBACK_STATUS/AI_GENERATED',
  `title`       VARCHAR(100) NOT NULL                COMMENT '通知标题（面板列表加粗展示）',
  `content`     VARCHAR(500) NOT NULL                COMMENT '通知正文',
  `link`        VARCHAR(200) DEFAULT NULL            COMMENT '点击跳转的前端路由，如 /tasks/1',
  `is_read`     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已读：0未读 1已读',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `is_read`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '站内通知表（系统事件推送）';

-- ----------------------------------------------------------------------------
-- 2. biz_message —— 业务对象评论表【人与人 · 围绕反馈/任务】
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_message`;
CREATE TABLE `biz_message` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `biz_type`    VARCHAR(20)   NOT NULL                COMMENT '业务对象类型：FEEDBACK/TASK',
  `biz_id`      BIGINT        NOT NULL                COMMENT '业务对象ID',
  `sender_id`   BIGINT        NOT NULL                COMMENT '发言人ID（sys_user.id）',
  `content`     VARCHAR(1000) NOT NULL                COMMENT '评论内容',
  `is_read`     TINYINT       NOT NULL DEFAULT 0      COMMENT '是否已被对象相关人查看：0未读 1已读（发言人自身记录无意义）',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_id`, `id`),
  KEY `idx_unread` (`is_read`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '业务对象评论表（反馈/任务下的留言时间线）';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 建表完成
-- ============================================================================
