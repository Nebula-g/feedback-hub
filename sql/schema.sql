-- ============================================================================
-- 企业报销软件用户反馈与功能改进助手 —— 数据库建表脚本
-- 数据库：MySQL 8.0
-- 字符集：utf8mb4
-- 版本：v1.0
-- 日期：2026-10-26
--
-- 设计要点：
--   1. 原始反馈（feedback）不可修改，不提供任何 update 业务接口
--   2. AI 产出与人工确认结果在物理上分离（ai_candidate_issue vs issue）
--   3. 每次 AI 调用全量落库（ai_generation_log），构成完整证据链
--   4. 所有状态变更同时写入事件表（task_event），构成可追溯时间线
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 创建数据库（如已存在可跳过此步）
CREATE DATABASE IF NOT EXISTS `feedback_hub`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `feedback_hub`;


-- ============================================================================
-- 1. sys_user —— 系统账号表（基础设施，非业务对象）
--    用途：登录认证与角色权限控制
-- ============================================================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username`      VARCHAR(50)  NOT NULL                COMMENT '登录账号，全局唯一',
  `password_hash` VARCHAR(100) NOT NULL                COMMENT 'BCrypt加密后的密码，禁止存明文',
  `display_name`  VARCHAR(50)  DEFAULT NULL            COMMENT '显示名称',
  `role`          VARCHAR(20)  NOT NULL                COMMENT '角色：CS-客服 PM-产品经理 DEV-开发测试 ADMIN-管理员',
  `dept`          VARCHAR(50)  DEFAULT NULL            COMMENT '所属部门',
  `status`        TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1-启用 0-禁用',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role` (`role`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '系统账号表';


-- ============================================================================
-- 2. feedback —— 原始反馈表【核心业务对象 · 只读】
--    用途：保存客服录入的脱敏用户反馈原文
--    重要：content 字段全程不可修改，系统不提供 update 接口
-- ============================================================================
DROP TABLE IF EXISTS `feedback`;
CREATE TABLE `feedback` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `feedback_no`  VARCHAR(32) NOT NULL                COMMENT '反馈编号，如 FB20261026-0001',
  `content`      TEXT                                COMMENT '反馈原文（脱敏），只读不可改',
  `module`       VARCHAR(30) NOT NULL                COMMENT '功能模块：EXPENSE_FORM-报销单填写 INVOICE_UPLOAD-发票上传 APPROVAL_PROGRESS-审批进度 RETURN_MODIFY-退回修改 OTHER-其他',
  `source`       VARCHAR(20) NOT NULL                COMMENT '来源渠道：TICKET-客服工单 SURVEY-使用调查 CALL-客户沟通',
  `customer_tag` VARCHAR(32) DEFAULT NULL            COMMENT '脱敏客户编号，如 C-0017，禁止存真实名称',
  `image_urls`   TEXT                                COMMENT '凭证图片地址 JSON 数组',
  `status`       VARCHAR(20) NOT NULL DEFAULT 'NEW'  COMMENT '状态：NEW-未归类 LINKED-已关联 CLOSED-已关闭 IGNORED-无效',
  `created_by`   BIGINT      NOT NULL                COMMENT '录入人（sys_user.id）',
  `deleted`      TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-正常 1-已删除',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`      INT         NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feedback_no` (`feedback_no`),
  KEY `idx_module_status` (`module`, `status`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '原始反馈表（只读）';


-- ============================================================================
-- 3. ai_generation_log —— AI 调用日志表【核心业务对象】
--    用途：记录每一次 AI 调用的输入输出全量内容
--    意义：构成「AI 说了什么 / 依据是什么 / 成功还是失败」的完整证据链
-- ============================================================================
DROP TABLE IF EXISTS `ai_generation_log`;
CREATE TABLE `ai_generation_log` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `biz_type`       VARCHAR(30)  NOT NULL                COMMENT '业务类型：EXTRACT-单条提取 CLUSTER-聚类成卡片 DRAFT-起草需求',
  `biz_id`         BIGINT       DEFAULT NULL            COMMENT '关联业务记录ID',
  `batch_no`       VARCHAR(32)  DEFAULT NULL            COMMENT '批次号，同一次批量生成共享',
  `provider`       VARCHAR(20)  NOT NULL                COMMENT '实现类型：LLM-真实大模型 RULE-本地规则兜底',
  `model`          VARCHAR(50)  DEFAULT NULL            COMMENT '模型名称，如 deepseek-chat',
  `input_snapshot` TEXT                                 COMMENT '输入快照（含所用反馈ID与内容）',
  `output_raw`     TEXT                                 COMMENT 'AI输出原文，未经加工，用于复盘与追责',
  `status`         VARCHAR(20)  NOT NULL                COMMENT '状态：SUCCESS-成功 FAILED-失败 TIMEOUT-超时',
  `error_msg`      VARCHAR(1000) DEFAULT NULL           COMMENT '失败原因',
  `cost_ms`        INT          DEFAULT NULL            COMMENT '耗时（毫秒）',
  `operator_id`    BIGINT       DEFAULT NULL            COMMENT '操作人',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = 'AI调用日志表';


-- ============================================================================
-- 4. ai_candidate_issue —— AI候选问题卡片表【核心业务对象 · 草稿】
--    用途：保存 AI 归纳出的候选问题，等待 PM 确认
--    重要：这是「草稿」，不是业务事实；确认后才生成正式 issue
-- ============================================================================
DROP TABLE IF EXISTS `ai_candidate_issue`;
CREATE TABLE `ai_candidate_issue` (
  `id`                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no`             VARCHAR(32)   DEFAULT NULL            COMMENT '生成批次号',
  `title`                VARCHAR(200)  NOT NULL                COMMENT '候选问题标题（AI生成，PM可修改）',
  `phenomenon`           TEXT                                  COMMENT '问题现象',
  `user_demand`          TEXT                                  COMMENT '用户诉求',
  `module`               VARCHAR(30)   NOT NULL                COMMENT '功能模块，聚类时作为硬分组依据',
  `ai_confidence`        DECIMAL(3, 2) DEFAULT NULL            COMMENT 'AI置信度 0.00-1.00',
  `ai_reason`            TEXT                                  COMMENT 'AI给出该归类的理由',
  `related_feedback_ids` TEXT                                  COMMENT '关联反馈ID的JSON数组',
  `status`               VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT-待处理 CONFIRMED-已确认 MODIFIED-修改后确认 MERGED-已合并 REJECTED-已驳回',
  `handled_by`           BIGINT        DEFAULT NULL            COMMENT '处理人（PM）',
  `handle_remark`        VARCHAR(1000) DEFAULT NULL            COMMENT '处理说明（驳回时必填）',
  `handle_time`          DATETIME      DEFAULT NULL            COMMENT '处理时间',
  `generated_issue_id`   BIGINT        DEFAULT NULL            COMMENT '确认后生成的正式问题ID',
  `deleted`              TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-正常 1-已删除',
  `create_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`              INT           NOT NULL DEFAULT 0      COMMENT '乐观锁，防并发重复确认',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_module` (`module`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = 'AI候选问题卡片表（草稿）';


-- ============================================================================
-- 5. issue —— 已确认问题表【核心业务对象 · 正式】
--    用途：保存经 PM 确认后的正式问题
--    重要：ai_draft_content（AI原话）与 final_content（PM定稿）并存，
--          两者不一致即证明 PM 做了修改，是验收场景5的直接证据
-- ============================================================================
DROP TABLE IF EXISTS `issue`;
CREATE TABLE `issue` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `issue_no`            VARCHAR(32)  NOT NULL                COMMENT '问题编号，如 IS-0001',
  `title`               VARCHAR(200) NOT NULL                COMMENT '问题标题',
  `description`         TEXT                                 COMMENT '问题描述',
  `module`              VARCHAR(30)  NOT NULL                COMMENT '功能模块',
  `ai_draft_content`    TEXT                                 COMMENT 'AI生成的需求草稿（保留原话，永不覆盖）',
  `final_content`       TEXT                                 COMMENT 'PM定稿的需求说明',
  `acceptance_criteria` TEXT                                 COMMENT '验收条件（PM确认后）',
  `source_type`         VARCHAR(20)  DEFAULT 'MANUAL'       COMMENT '来源：AI-源自候选卡片 MANUAL-PM手工创建',
  `confirmed_by`        BIGINT       DEFAULT NULL            COMMENT '确认人（PM）',
  `confirm_time`        DATETIME     DEFAULT NULL            COMMENT '确认时间',
  `deleted`             TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-正常 1-已删除',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`             INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_no` (`issue_no`),
  KEY `idx_module` (`module`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '已确认问题表（正式）';


-- ============================================================================
-- 6. feedback_issue_link —— 反馈与问题关联表【核心业务对象】
--    用途：建立反馈与问题的多对多关系，实现「从问题回查原文」
--    设计原因：一条反馈可含多个问题，一个问题可由多条反馈归纳
-- ============================================================================
DROP TABLE IF EXISTS `feedback_issue_link`;
CREATE TABLE `feedback_issue_link` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `feedback_id`  BIGINT      NOT NULL                COMMENT '反馈ID（feedback.id）',
  `issue_id`     BIGINT      NOT NULL                COMMENT '问题ID（issue.id）',
  `candidate_id` BIGINT      DEFAULT NULL            COMMENT '来源候选卡片ID，手工关联时为空',
  `link_type`    VARCHAR(20) NOT NULL                COMMENT '关联类型：AI-AI建议 PM-人工关联',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feedback_issue` (`feedback_id`, `issue_id`),
  KEY `idx_issue_id` (`issue_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '反馈与问题关联表';


-- ============================================================================
-- 7. improvement_task —— 改进任务表【核心业务对象 · 正式】
--    用途：由已确认问题转化而来的可执行开发任务
--    重要：priority 字段完全由 PM 手选，AI 仅提供 ai_suggested_priority 参考
-- ============================================================================
DROP TABLE IF EXISTS `improvement_task`;
CREATE TABLE `improvement_task` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_no`               VARCHAR(32)  NOT NULL                COMMENT '任务编号，如 TK-0001',
  `issue_id`              BIGINT       NOT NULL                COMMENT '关联问题ID（issue.id）',
  `title`                 VARCHAR(200) NOT NULL                COMMENT '任务标题',
  `requirement`           TEXT                                 COMMENT '需求说明（由PM定稿带入）',
  `acceptance_criteria`   TEXT                                 COMMENT '验收条件',
  `priority`              VARCHAR(10)  NOT NULL                COMMENT '优先级 P0/P1/P2，由产品经理手动设定',
  `priority_reason`       VARCHAR(1000) DEFAULT NULL           COMMENT 'PM填写的定级理由',
  `ai_suggested_priority` VARCHAR(10)  DEFAULT NULL            COMMENT 'AI建议优先级，仅供参考，不自动写入priority',
  `ai_evidence`           TEXT                                 COMMENT 'AI提供的客观统计依据 JSON',
  `assignee_id`           BIGINT       DEFAULT NULL            COMMENT '负责人（DEV）',
  `status`                VARCHAR(20)  NOT NULL DEFAULT 'TODO' COMMENT '状态：TODO-待开发 IN_PROGRESS-开发中 PENDING_VERIFY-待验证 DONE-已完成 WONT_DO-已关闭',
  `created_by`            BIGINT       NOT NULL                COMMENT '创建人（PM）',
  `deleted`               TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-正常 1-已删除',
  `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`              INT          NOT NULL DEFAULT 0      COMMENT '乐观锁，防并发状态冲突',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_issue_id` (`issue_id`),
  KEY `idx_status_priority` (`status`, `priority`),
  KEY `idx_assignee` (`assignee_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '改进任务表（正式）';


-- ============================================================================
-- 8. task_event —— 任务事件表【核心业务对象 · 时间线】
--    用途：记录任务每一次状态变更与验证结果
--    重要：operator_role 存快照，避免人员角色变动导致历史失真
-- ============================================================================
DROP TABLE IF EXISTS `task_event`;
CREATE TABLE `task_event` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id`       BIGINT      NOT NULL                COMMENT '任务ID（improvement_task.id）',
  `from_status`   VARCHAR(20) DEFAULT NULL            COMMENT '原状态，创建时为NULL',
  `to_status`     VARCHAR(20) NOT NULL                COMMENT '新状态',
  `operator_id`   BIGINT      NOT NULL                COMMENT '操作人ID',
  `operator_role` VARCHAR(20) NOT NULL                COMMENT '操作人角色快照（CS/PM/DEV）',
  `remark`        TEXT                                COMMENT '处理说明（打回、关闭时必填）',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '任务事件表（时间线）';


SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 建表完成
-- 下一步：执行 init-data.sql 初始化演示数据
-- ============================================================================
