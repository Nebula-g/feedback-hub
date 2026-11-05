# 数据库设计说明（第三章成果）

> 数据库：MySQL 8.0 ｜ 字符集：utf8mb4 ｜ 排序规则：utf8mb4_general_ci
> 版本：v1.0 ｜ 日期：2026-10-26

---

## 一、设计总览

### 1.1 表清单

| # | 表名 | 中文名 | 类型 | 说明 |
| ---: | --- | --- | --- | --- |
| 1 | `sys_user` | 系统账号 | 基础设施 | 登录与角色 |
| 2 | `feedback` | 原始反馈 | **核心业务对象** | 只读，不可修改 |
| 3 | `ai_generation_log` | AI 调用日志 | **核心业务对象** | 全量输入输出留痕 |
| 4 | `ai_candidate_issue` | AI 候选问题卡片 | **核心业务对象** | 草稿，AI 产出 |
| 5 | `issue` | 已确认问题 | **核心业务对象** | 正式，PM 确认 |
| 6 | `feedback_issue_link` | 反馈问题关联 | **核心业务对象** | 多对多，可回查 |
| 7 | `improvement_task` | 改进任务 | **核心业务对象** | 正式，可执行 |
| 8 | `task_event` | 任务事件 | **核心业务对象** | 时间线，可追溯 |

> 核心业务对象 **7 个**，符合课程建议的「4 至 7 个核心业务对象」。

### 1.2 关系图（ER）

```
                    ┌──────────────┐
                    │   sys_user   │
                    └──────┬───────┘
           created_by      │      assignee / operator
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌───────────────┐   ┌─────────────┐   ┌──────────────────┐
│   feedback    │   │    issue    │   │ improvement_task │
│  （只读原文） │   │  （正式）   │   │    （正式）      │
└───────┬───────┘   └──────┬──────┘   └────────┬─────────┘
        │                  │                   │
        │    ┌─────────────┴─────────┐         │
        └───►│  feedback_issue_link  │◄────────┘ (via issue)
             │  （多对多 · 可回查）  │
             └───────────┬───────────┘
                         │
                         ▼
             ┌───────────────────────┐
             │  ai_candidate_issue   │  ← AI 草稿
             │   （status=DRAFT）    │
             └───────────┬───────────┘
                         │
                         ▼
             ┌───────────────────────┐
             │  ai_generation_log    │  ← 输入输出全量留痕
             └───────────────────────┘

improvement_task ──1:N──► task_event（状态变更时间线）
```

### 1.3 核心设计原则：三层分离

这是本项目最重要的设计，直接对应评分维度「业务规则与权限」15%：

```
┌──────────────────────────────────────────────────┐
│ 第 1 层：原始素材（不可变）                       │
│   feedback 表 —— 无 update 业务接口               │
└──────────────────────────────────────────────────┘
                    ↓ AI 归纳
┌──────────────────────────────────────────────────┐
│ 第 2 层：AI 草稿（可丢弃、可覆盖、可驳回）        │
│   ai_candidate_issue 表                          │
│   issue.ai_draft_content 字段                    │
│   improvement_task.ai_suggested_priority 字段     │
└──────────────────────────────────────────────────┘
                    ↓ 人工确认
┌──────────────────────────────────────────────────┐
│ 第 3 层：正式结果（业务事实）                     │
│   issue 表（final_content）                      │
│   improvement_task 表（priority，PM 手选）        │
└──────────────────────────────────────────────────┘

全程由 ai_generation_log 记录每一次「AI 说了什么」
```

---

## 二、表结构详细设计

### 2.1 `sys_user` —— 系统账号

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, AUTO_INCREMENT | 主键 |
| `username` | VARCHAR(50) | UNIQUE, NOT NULL | 登录账号 |
| `password_hash` | VARCHAR(100) | NOT NULL | BCrypt 加密，不存明文 |
| `display_name` | VARCHAR(50) | | 显示名称 |
| `role` | VARCHAR(20) | NOT NULL | `CS` / `PM` / `DEV` / `ADMIN` |
| `dept` | VARCHAR(50) | | 所属部门 |
| `status` | TINYINT | DEFAULT 1 | 1-启用 0-禁用 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |
| `update_time` | DATETIME | ON UPDATE CURRENT_TIMESTAMP | |

---

### 2.2 `feedback` —— 原始反馈（**只读**）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK | 主键 |
| `feedback_no` | VARCHAR(32) | UNIQUE, NOT NULL | 反馈编号 `FB20261026-0001` |
| `content` | TEXT | NOT NULL | **反馈原文，全程不可修改** |
| `module` | VARCHAR(30) | NOT NULL | 功能模块枚举 |
| `source` | VARCHAR(20) | NOT NULL | 来源渠道枚举 |
| `customer_tag` | VARCHAR(32) | | 脱敏客户编号 `C-0017` |
| `image_urls` | TEXT | | 图片地址 JSON 数组 |
| `status` | VARCHAR(20) | DEFAULT 'NEW' | `NEW`/`LINKED`/`CLOSED`/`IGNORED` |
| `created_by` | BIGINT | NOT NULL | 录入人 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |
| `update_time` | DATETIME | ON UPDATE CURRENT_TIMESTAMP | |
| `version` | INT | DEFAULT 0 | 乐观锁 |

**枚举值**：
```
module:  EXPENSE_FORM | INVOICE_UPLOAD | APPROVAL_PROGRESS | RETURN_MODIFY | OTHER
source:  TICKET（客服工单）| SURVEY（使用调查）| CALL（客户沟通）
status:  NEW | LINKED | CLOSED | IGNORED
```

> **⚠️ 业务约定**：`content` 字段不提供任何 update 接口。客服如需修正，
> 只能新增一条反馈并备注说明，原记录永久保留。

---

### 2.3 `ai_generation_log` —— AI 调用日志

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK | 主键 |
| `biz_type` | VARCHAR(30) | NOT NULL | `EXTRACT`（提取）/`CLUSTER`（聚类）/`DRAFT`（起草） |
| `biz_id` | BIGINT | | 关联业务记录 ID |
| `batch_no` | VARCHAR(32) | | 批次号，同批生成共享 |
| `provider` | VARCHAR(20) | NOT NULL | `LLM` / `RULE`（区分真实模型与规则兜底） |
| `model` | VARCHAR(50) | | 模型名称 |
| `input_snapshot` | TEXT | | **输入快照**（含所用反馈 ID 与内容） |
| `output_raw` | TEXT | | **输出原文**（未加工，用于追责与复盘） |
| `status` | VARCHAR(20) | NOT NULL | `SUCCESS` / `FAILED` / `TIMEOUT` |
| `error_msg` | VARCHAR(1000) | | 失败原因 |
| `cost_ms` | INT | | 耗时（毫秒） |
| `operator_id` | BIGINT | | 操作人 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |

> **这张表是整个 AI 模块的证据链核心**。答辩时可现场查询：
> 「AI 当时的输入是什么、原话输出是什么、成功还是失败、耗时多少」。

---

### 2.4 `ai_candidate_issue` —— AI 候选问题卡片（草稿）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK | 主键 |
| `batch_no` | VARCHAR(32) | | 生成批次 |
| `title` | VARCHAR(200) | NOT NULL | 候选问题标题（AI 生成，PM 可改） |
| `phenomenon` | TEXT | | 问题现象 |
| `user_demand` | TEXT | | 用户诉求 |
| `module` | VARCHAR(30) | NOT NULL | 功能模块（**硬分组依据**） |
| `ai_confidence` | DECIMAL(3,2) | | AI 置信度 0.00–1.00 |
| `ai_reason` | TEXT | | AI 给出该归类的理由 |
| `related_feedback_ids` | TEXT | | 关联反馈 ID 的 JSON 数组 |
| `status` | VARCHAR(20) | DEFAULT 'DRAFT' | `DRAFT`/`CONFIRMED`/`MODIFIED`/`MERGED`/`REJECTED` |
| `handled_by` | BIGINT | | 处理人（PM） |
| `handle_remark` | VARCHAR(1000) | | 处理说明（驳回时**必填**） |
| `handle_time` | DATETIME | | |
| `generated_issue_id` | BIGINT | | 确认后生成的正式问题 ID |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |
| `update_time` | DATETIME | ON UPDATE CURRENT_TIMESTAMP | |
| `version` | INT | DEFAULT 0 | 乐观锁（防并发重复确认） |

---

### 2.5 `issue` —— 已确认问题（正式）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK | 主键 |
| `issue_no` | VARCHAR(32) | UNIQUE | 问题编号 `IS-0001` |
| `title` | VARCHAR(200) | NOT NULL | 问题标题 |
| `description` | TEXT | | 问题描述 |
| `module` | VARCHAR(30) | NOT NULL | 功能模块 |
| `ai_draft_content` | TEXT | | **AI 生成的需求草稿（保留原话）** |
| `final_content` | TEXT | | **PM 定稿的需求说明** |
| `acceptance_criteria` | TEXT | | 验收条件（PM 确认后） |
| `source_type` | VARCHAR(20) | | `AI`（源自候选卡片）/ `MANUAL`（PM 手工创建） |
| `confirmed_by` | BIGINT | | 确认人（PM） |
| `confirm_time` | DATETIME | | |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |
| `update_time` | DATETIME | ON UPDATE CURRENT_TIMESTAMP | |
| `version` | INT | DEFAULT 0 | 乐观锁 |

> **关键证据点**：`ai_draft_content` 与 `final_content` 并存。
> 若两者不一致，即证明 PM 对 AI 草稿做了修改——这正是验收场景 5 的要求。

---

### 2.6 `feedback_issue_link` —— 反馈与问题关联

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK | 主键 |
| `feedback_id` | BIGINT | NOT NULL | 反馈 ID |
| `issue_id` | BIGINT | NOT NULL | 问题 ID |
| `candidate_id` | BIGINT | | 来源候选卡片（可空，手工关联时为空） |
| `link_type` | VARCHAR(20) | NOT NULL | `AI`（AI 建议）/ `PM`（人工关联） |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |

**唯一约束**：`UNIQUE KEY uk_feedback_issue (feedback_id, issue_id)`
—— 防止同一条反馈被重复关联到同一个问题。

> **多对多设计的原因**：一条反馈可包含多个问题（拆成多张卡片），
> 一个问题也可由多条反馈归纳而来。这个关联表是「可回查」的物理基础。

---

### 2.7 `improvement_task` —— 改进任务（正式）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK | 主键 |
| `task_no` | VARCHAR(32) | UNIQUE | 任务编号 `TK-0001` |
| `issue_id` | BIGINT | NOT NULL | 关联问题 |
| `title` | VARCHAR(200) | NOT NULL | 任务标题 |
| `requirement` | TEXT | | 需求说明（PM 定稿后带入） |
| `acceptance_criteria` | TEXT | | 验收条件 |
| `priority` | VARCHAR(10) | NOT NULL | `P0`/`P1`/`P2` —— **由 PM 手选** |
| `priority_reason` | VARCHAR(1000) | | PM 填写的定级理由 |
| `ai_suggested_priority` | VARCHAR(10) | | AI 建议分（仅供参考） |
| `ai_evidence` | TEXT | | AI 提供的客观依据 JSON |
| `assignee_id` | BIGINT | | 负责人（DEV） |
| `status` | VARCHAR(20) | DEFAULT 'TODO' | `TODO`/`IN_PROGRESS`/`PENDING_VERIFY`/`DONE`/`WONT_DO` |
| `created_by` | BIGINT | NOT NULL | 创建人（PM） |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |
| `update_time` | DATETIME | ON UPDATE CURRENT_TIMESTAMP | |
| `version` | INT | DEFAULT 0 | 乐观锁（防并发状态冲突） |

**`ai_evidence` JSON 结构示例**：
```json
{
  "feedbackCount": 7,
  "customerCount": 5,
  "module": "INVOICE_UPLOAD",
  "blocking": true,
  "firstSeenAt": "2026-10-20",
  "lastSeenAt": "2026-10-26"
}
```

> **约束 A4 落地点**：AI 只输出上述**可统计的客观事实**和**建议分**，
> `priority` 字段本身完全由 PM 手动选择，AI 无任何写入权限。

---

### 2.8 `task_event` —— 任务事件（时间线）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK | 主键 |
| `task_id` | BIGINT | NOT NULL | 任务 ID |
| `from_status` | VARCHAR(20) | | 原状态（创建时为 NULL） |
| `to_status` | VARCHAR(20) | NOT NULL | 新状态 |
| `operator_id` | BIGINT | NOT NULL | 操作人 |
| `operator_role` | VARCHAR(20) | NOT NULL | 操作人角色（快照，防角色变更导致历史失真） |
| `remark` | TEXT | | 处理说明（**打回、关闭时必填**） |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |

> **设计细节**：`operator_role` 存快照而非关联查询。
> 原因：若某人角色从 DEV 变更为 PM，历史记录不应随之改变。

---

## 三、状态机汇总

### 3.1 状态总览

```
feedback                    ai_candidate_issue              improvement_task
────────                    ──────────────────              ────────────────
NEW                         DRAFT                           TODO
  ↓                           ├→ CONFIRMED ─┐                 ↓
LINKED                        ├→ MODIFIED ─┼→ 生成 issue     IN_PROGRESS
  ↓                           ├→ MERGED ───┘                  ↓
CLOSED                        └→ REJECTED                    PENDING_VERIFY
                                                              ↓
IGNORED                                                      DONE

                                              TODO ──→ WONT_DO
                                              PENDING_VERIFY → IN_PROGRESS（打回，需填原因）
```

### 3.2 禁止的状态跳转

| 对象 | 禁止跳转 | 原因 |
| --- | --- | --- |
| feedback | `LINKED → NEW` | 需 PM 解除关联并留记录 |
| feedback | `CLOSED → *` | 终态不可逆 |
| candidate | `REJECTED → CONFIRMED` | 必须重新生成候选 |
| candidate | `CONFIRMED/MERGED → DRAFT` | 已确认结果不可逆 |
| task | `TODO → DONE` | 必须走完全流程 |
| task | `TODO → PENDING_VERIFY` | 不得跳过"开发中" |
| task | `DONE → *` | 终态不可逆 |

### 3.3 必填说明的操作

| 操作 | 必填项 |
| --- | --- |
| 驳回候选卡片 | `handle_remark` |
| 关闭任务为 WONT_DO | `remark` |
| 验证不通过打回 | `remark` |
| 判定反馈为 IGNORED | 处理说明 |

---

## 四、索引设计

| 表 | 索引 | 字段 | 用途 |
| --- | --- | --- | --- |
| feedback | `uk_feedback_no` | `feedback_no` | 编号唯一 |
| feedback | `idx_module_status` | `module, status` | 按模块+状态筛选（聚类主查询） |
| feedback | `idx_created_by` | `created_by` | 客服查看自己录入的 |
| feedback | `idx_create_time` | `create_time` | 时间范围查询 |
| ai_candidate_issue | `idx_status` | `status` | PM 工作台待办列表 |
| ai_candidate_issue | `idx_batch_no` | `batch_no` | 按批次查看 |
| ai_generation_log | `idx_biz` | `biz_type, biz_id` | 查看某业务的 AI 历史 |
| issue | `uk_issue_no` | `issue_no` | 编号唯一 |
| issue | `idx_module` | `module` | 按模块统计 |
| improvement_task | `uk_task_no` | `task_no` | 编号唯一 |
| improvement_task | `idx_status_priority` | `status, priority` | 看板与排序 |
| improvement_task | `idx_assignee` | `assignee_id` | DEV 我的任务 |
| feedback_issue_link | `uk_feedback_issue` | `feedback_id, issue_id` | 防重复关联 |
| task_event | `idx_task_id` | `task_id` | 时间线查询 |

---

## 五、数据一致性约定

| # | 约定 |
| ---: | --- |
| 1 | 所有业务表使用**逻辑删除**（`deleted` 字段）或按业务规则保留，不做物理删除 |
| 2 | 所有状态变更必须在**同一事务**内写入主表与事件表 |
| 3 | 所有表含 `create_time` / `update_time`，由数据库自动维护 |
| 4 | 涉及并发修改的表（`ai_candidate_issue` / `improvement_task`）加 `version` 乐观锁 |
| 5 | 金额、客户名称、手机号等敏感字段**一律不入库** |
| 6 | `operator_role` 等操作人属性存**快照**，不依赖关联查询 |
