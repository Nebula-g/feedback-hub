# 核心业务链路验收复测报告（2026-11-05）

> 触发：用户给出原始验收需求（8 条核心验收场景 + AI 业务约束）并要求"测试"。
> 方式：实时接口探测（curl）+ 数据库只读核对（MySQL 3308/feedback_hub）+ 代码核查。
> 与 11-04 两份报告的关系：那份报告的 3 项遗留问题据称已修复，本次**验证是否真的通了**，并检查此后新增的代码。
> ⚠️ 本轮全部为**只读**操作，未新增/修改任何业务数据。

## 一、环境实况

| 项 | 状态 |
|---|---|
| 后端 8081 / 前端 5173 / MySQL 3308 | ✅ 全部 LISTENING |
| 登录 | ✅ `pm01/pm123456` 等 4 账号可用（README §4.5 口令仍准确） |
| 数据库表 | ⚠️ **实际 10 张**，`sql/schema.sql` 只有 **8 张**（见 §三 F-1） |
| 新接口是否已热部署 | ✅ `/candidates/{id}/merge`、`/api/notifications`、`/api/messages` 均在线 |

## 二、8 条验收场景复核结果

| # | 验收场景 | 11-04 结论 | 本次复核 | 依据 |
|---|---|---|---|---|
| 1 | 多条不同说法 → 生成候选并保留原文 | ✅ | ✅ 成立 | 红线只读核查：`feedback` 无任何 AI 改写路径；候选经 `related_feedback_ids` 回链（库里 MERGED 卡 `65→[80]`、`68→[81,82]` 可见） |
| 2 | 复合反馈拆成两个候选 | ✅ | ✅ 成立 | `ai_candidate_issue.status` 存在 `SPLITTED=1`，拆分接口 `/split` 在线（PM/ADMIN） |
| 3 | 关键词相似但含义不同 → 未经确认不成候选 | ⚠️ | ✅ **红线成立** | 只读核查 `status='DRAFT' AND generated_issue_id IS NOT NULL` = **0 条**（草稿与事实物理分离未被破坏） |
| 4 | PM 可合并/修改/驳回 | ⚠️ 合并无接口 | ✅ **已修复并实测通** | `/merge` 返回 400「目标问题不能为空」（路由存在+参数校验生效，非 404）；库中 3 张 `MERGED` 卡片为执行留痕 |
| 5 | AI 需求草稿须 PM 确认才建任务 | ✅ | ✅ 成立 | `/issues/{id}/draft` 仅返回文本；建任务唯一入口 `POST /api/tasks`（PM） |
| 6 | 开发/测试更新任务并记录验证结果 | ✅ | ✅ 成立 | 状态机 `TaskStatus` 显式后继集合 + `remark` 必填；`task_event` 时间线 |
| 7 | 普通客服不能定优先级/删原始反馈 | ✅ | ✅ 成立 | `PUT /tasks/{id}/priority` = PM；`DELETE /api/feedbacks/{id}` 接口不存在（设计上只读） |
| 8 | 模型不可用仍可手工分类建任务 | ✅ | ⚠️ **代码在，当前库无证据** | `ai_generation_log` 状态分布 `SUCCESS=15 / FAILED=1 / TIMEOUT=1 / **FALLBACK=0**` |

**AI 业务约束核查**：候选表无 `priority` 列（information_schema 计数 = 0）→ "AI 不决定优先级"是结构级保证，不只是约定。✅

## 三、本轮新发现的问题

### 🔴 F-1【阻断级】新模块的两张表没进主建表脚本，全新安装会当场报错

- 事实：库里有 `notification`、`biz_message`；但 `sql/schema.sql` 与 `sql/init-data.sql` 中 `grep -c "biz_message\|notification"` = **0**；这两张表的建表语句只存在于 **`sql/notification-message.sql`，且该文件未被 git 跟踪**（`git status` → `?? sql/notification-message.sql`）。
- 后果（推断，高置信）：任何按 README §4.2 操作的干净环境（`mysql < sql/schema.sql` + `init-data.sql`）只会建 8 张表，后端能正常启动，但登录后主框架 `MainLayout.vue:42 onMounted → noticeStore.startPolling()` 会立刻并持续轮询 `/api/notifications/unread-count` 与 `/api/messages/unread-groups` → 表不存在 → 500。
- 为什么现在没暴露：本机库已被手工补过表。**这个坑只砸在"换机器/重装/交给别人跑"的时候** —— 恰好是交付场景。
- 修复：① 把两张表并入 `sql/schema.sql`（单一真源）；② `scripts/reset_demo_data.sh` 增加存在性检查，缺表即建；③ README §4.2 命令保持一条不漏。
- **验收**：新建空库 → 只执行 README 的两条 mysql 命令 → 启动后端 → 登录 → 通知铃铛不报错、`information_schema` 计数 = 10。

### 🟠 F-2 已证实的权限口径 bug 仍在（评审 R-03）

实时复现：`GET /api/ai/logs` → PM **200** / DEV **403** / CS **403**，
而前端 `router/index.js:103` 的 `roles:['PM','ADMIN','DEV']` 会把菜单和路由放行给 DEV。
→ dev01 看得见「AI 调用记录」入口，点进去必然失败。上次提交 `ad2c9ae` 正是为对齐口径，这条漏了。

### 🟠 F-3 演示库已严重偏离 README 宣称状态

当前 `ai_candidate_issue` 有 **49 张 DRAFT**、`feedback` 有 **64 条 NEW**；README §7.3 声称 reset 后应为"60 条反馈 / 0 张候选卡片"。
根因：`reset_demo_data.sh` 只灌 `init-data.sql`，**既不清 `ai_candidate_issue`，也不建 notification/biz_message**。
→ 演示前不手动清库的话，PM 一进候选页就是 49 张陈旧卡片，验收场景 1「导入后生成候选」的可读性被完全淹没。

### 🟡 F-4 招牌场景 FALLBACK 无留证

`ai_generation_log` 里 FALLBACK = 0 条。代码路径确实存在（`AiCandidateServiceImpl.java:441-451`），11-04 报告也称实测过，但当前库无记录 → 答辩时无法直接翻证据链。建议演示前跑一次 `AI_SIMULATE_FAILURE=true` 并**保留**那条 FALLBACK 日志，别 reset 掉。

### 🟡 F-5 我先前给的架构模型已过期

上一轮交付的 `docs/architecture/` 是按 8 表 / 7 Controller / 12 页面画的。当前实际是 **10 表 / 9 Controller / 新增 dashboard+notification 页面与 components 目录**。需要重跑一次 `system-modeler` 才能作为基线继续用。

## 四、顺带确认：新增的消息/通知模块质量尚可

- `MessageServiceImpl.send()` 的 `senderId` 取自服务端 `UserContext.getUserId()`，**不信任客户端传入** → 无法伪造发件人；`bizType` 有白名单校验、发送前 `requireBizExists` 校验对象存在。
- 两个新 Controller 无 `@PreAuthorize`：对个人收件箱/本人评论是合理的（按当前用户过滤），但建议补一条"只能读/改自己的记录"的断言测试。

## 五、我没做的事（需要你决定）

1. **写路径 E2E 实测**（真实导入反馈→生成候选→确认→建任务→流转）会往当前库再加数据；在 49 张 DRAFT 已堆着的情况下，建议先清库再跑，否则结果不可读。清库是**破坏性**操作，我没有动。
2. **F-1 的修复**（合并建表脚本 + reset 脚本加存在性检查）我可以直接改。
3. **F-2 的修法需要你给产品答案**：DEV 到底该不该看 AI 调用记录？（放行 DEV → 改后端注解；不放行 → 删前端 roles 里的 'DEV'。）
