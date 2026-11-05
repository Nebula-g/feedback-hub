# Feedback Hub 架构理解（当前态）

> 由 `system-modeler`（+ `c4model`、`graphviz`）生成 · 2026-11-02
> 阅读顺序：§1 系统是什么 → §2 边界与容器 → §3 业务闭环 → §4 权限 → §5 数据与状态 → §6 关键设计 → §7 未知项
> 逐条证据索引：`system-model.evidence.md`

## 1. 系统边界（一句话）

Feedback Hub 是**报销软件公司内部**的单系统闭环工具：客服录入用户反馈 → AI 辅助归并出候选问题 → 产品经理确认成正式问题 → 建改进任务 → 开发测试流转验证 → 全程留证可追溯。系统边界内只有一个可部署业务后端；边界外是浏览器用户、本机 MySQL、本地文件目录、可选的外部大模型 API。

## 2. 容器与调用关系（L2）

| 容器 | 技术 | 端口/位置 | 证据强度 |
|---|---|---|---|
| 前端 SPA | Vue 3 + Vite + Pinia + Naive UI | dev 5173 / prod 静态包 | high |
| Nginx | 静态托管 + `/api` 反代 | prod 8080 | high（`deploy/nginx/feedback.conf`）|
| 后端单体 | Spring Boot 3.2（JDK 17, MyBatis-Plus, JJWT） | 8081，`feedback-hub.jar` | high |
| MySQL 8.0 | schema `feedback_hub`，8 张表 | 本机 3308 | high |
| 本地 uploads | 截图文件，`/uploads/**` 直链 | 磁盘目录 | high |
| 外部大模型 API | OpenAI 兼容（默认配 deepseek） | 可选启用 | 存在 high / 运行时使用 medium |
| 阿里云 OSS | `storage.type=oss` 才装配 | 未启用 | low |

单体内部组件（L3）：`controller×7 → service 接口 → service/impl（含 AI 编排 + 双实现 + 存储抽象）→ mapper×8 → entity/enums`，`security/` JWT 过滤器横切所有请求，`config/` 提供 Ai/Storage/Security 装配。**注意：组件图是同一 JVM 内的包结构，不是微服务。**

## 3. 核心业务闭环（见 `business-flow.dot`）

1. **录入**：CS 经 `POST /api/feedbacks`（或 `/batch`）写 `feedback`；截图走 `/api/files/upload`。
2. **AI 归并**：PM 触发 `POST /api/ai/candidates/generate`；编排层按配置选 Rule/Llm 实现，失败自动降级并记 `FALLBACK`；产出写草稿表 `ai_candidate_issue`，每次调用全量落 `ai_generation_log`。
3. **确认/驳回**：`confirm` 经 `IssueService.confirmFromCandidate`（事务 + `@Version` 乐观锁）生成 `issue` 并建 `feedback_issue_link`；`reject` 后关联反馈回到未归类，数据不丢失。
4. **任务**：PM 建 `improvement_task`、手选优先级、指派 DEV。
5. **流转**：DEV 经 `PUT /api/tasks/{id}/status` 沿 `TODO→IN_PROGRESS→PENDING_VERIFY→DONE`（可 `WONT_DO`）推进，每步追加 `task_event`（append-only 时间线）。

## 4. 权限边界（前后端口径一致）

- 结构：`sys_user.role` + 方法级 `@PreAuthorize` + 路由 `meta.roles` 三层同名角色（CS/PM/DEV/ADMIN）。
- 关键已证实规则：反馈写入仅 CS/ADMIN；反馈**正文无修改接口**（只读红线）；AI 生成/确认/驳回仅 PM/ADMIN；`GET /api/ai/status` 仅 ADMIN；任务状态流转 DEV+PM；建任务/优先级/指派 PM。
- 详见 evidence.md 权限矩阵表。

## 5. 数据与状态所有权

| 表 | 所有权/生命周期 | 特性 |
|---|---|---|
| `feedback` | CS 创建；status 由归类/确认/驳回路径回写 | 正文只读 |
| `ai_candidate_issue` | AI 编排创建；DRAFT→CONFIRMED/REJECTED | 草稿区，乐观锁 |
| `issue` | 仅 PM 确认或手动创建（`IssueSourceType` 区分来源） | 正式事实 |
| `feedback_issue_link` | 确认/手动挂链写入；`LinkType` | 多对多 |
| `improvement_task` | PM 创建；`TaskStatus` 状态机 | `@Version` |
| `task_event` / `ai_generation_log` | 系统写入 | **append-only 证据链** |
| `sys_user` | ADMIN | 账号+角色 |

## 6. 最能解释"为什么这样设计"的三点

- **草稿与事实物理分离**：AI 产出与正式问题分表，PM 确认是唯一跨界动作（有事务边界证据）。
- **降级即地板**：规则实现零依赖先行，LLM 只是增强；`fallback=true` 会回传前端做诚实提示（`AiGenerateResult.java:13-14,38`）。
- **全量留证**：AI 输入快照/原文/耗时/状态与任务时间线都不可变落库，支撑"闭环可追溯"验收。

## 7. 未知项（不作为已证实架构）

1. 生产环境是否实际启用 `AI_PROVIDER=llm`（需运行时日志抽样验证）。
2. OSS 链路未经真实凭据验证。
3. `FeedbackStatus` 各状态间流转约束强度未逐条核对 —— 需要时路由 `flow-visualizer` 深入。
4. 部署仅本机单机方案，无多环境拓扑证据。

## 维护说明

- 三个模型源文件：`system-model.structurizr.dsl`（C4 上下文/容器/组件三视图）、`business-flow.dot`（业务闭环）、`module-dependencies.dot`（包级依赖）。
- DSL 可用 Qoder 的 Structurizr DSL 预览打开；DOT 可用 DOT 格式预览打开。
- 重大结构性变更（新增容器/表/角色/端点组）后应同步更新本目录，并让 `architecture-health` 做新鲜度核对。
