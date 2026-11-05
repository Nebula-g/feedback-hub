# System Model — 证据索引

生成时间：2026-11-02 · 技能组合：`system-modeler` + `c4model` + `graphviz` · 状态：均为**当前态**（current-state）
置信度定义见架构契约：high=直接代码/配置证据；medium=多信号一致但无直接确认；low=按命名/结构推断。

## 节点证据

| 节点 | 置信度 | 证据（sourceRefs） |
|---|---|---|
| 4 类角色 CS/PM/DEV/ADMIN | high | `enums/RoleEnum.java`；`sql/schema.sql:31`（sys_user）；README §4.5 演示账号；控制器 `@PreAuthorize` 全量核对 |
| 前端 SPA（Vue3+Vite+Pinia+Naive UI） | high | `frontend/package.json`、`vite.config.js`、`src/` 目录树（api/router/stores/views 12 页面） |
| Nginx 托管+反代 | high | `deploy/nginx/feedback.conf`；README §5.3（proxy_pass 不带斜杠、SPA try_files 回退） |
| 后端 API 单体（:8081） | high | `application.yml:6`（server.port=8081）；`FeedbackHubApplication.java`；`backend/run.sh` |
| security 包（JWT） | high | `security/JwtAuthenticationFilter.java`、`JwtTokenProvider.java`、`config/SecurityConfig.java:124-138` |
| controller ×7 | high | `controller/*.java`（Auth/Feedback/Ai/Issue/Task/File/User），`@RequestMapping` 逐条核对 |
| AiCandidateServiceImpl 编排+降级 | high | `service/impl/AiCandidateServiceImpl.java:87-90,152-160,403-451,489`（provider 选择、FALLBACK 状态回写） |
| Rule/Llm 双实现 | high | `service/impl/RuleAiServiceImpl.java`、`LlmAiServiceImpl.java:259`（simulate-failure 开关） |
| FileStorage 抽象 local/oss | high | `service/impl/LocalFileStorageService.java:37`、`OssFileStorageService.java:37`（`@ConditionalOnProperty storage.type`） |
| mapper ×8 / MyBatis-Plus | high | `mapper/*.java` 8 个文件；`config/MybatisPlusConfig.java`；README 分页+乐观锁说明 |
| MySQL feedback_hub（3308，8 表） | high | `application.yml:25`（jdbc URL）；`sql/schema.sql:31,55,85,114,149,179,200,234` |
| 本地 uploads 目录 | high | `application.yml:95`（url-prefix `/uploads/**`）；`SecurityConfig` permitAll `/uploads/**` |
| 外部大模型 API | high（存在）/ medium（运行时是否实际调用取决于环境变量） | `AiProperties.java`、`application.yml:123`（base-url 默认 deepseek）；README §6.2 |
| 阿里云 OSS | low（当前未启用） | `OssFileStorageService.java` 存在但 `storage.type` 默认 local（`matchIfMissing=true`） |

## 关系证据

| 边 | 置信度 | 证据 |
|---|---|---|
| Nginx → 后端 `/api/` 反代 | high | `deploy/nginx/feedback.conf`；README §5.3 |
| 前端 → 后端（开发期 Vite proxy） | high | `vite.config.js:44-52`（/api、/uploads → localhost:8081） |
| 路由级角色守卫（前端） | high | `router/index.js:42,60,103,151-152`（meta.roles + 守卫） |
| AI 调用 → ai_generation_log 全量留证 | high | `AiCandidateServiceImpl` 状态回填逻辑；`enums/AiLogStatus.java:41`（FALLBACK）；`sql/schema.sql:85` |
| LLM 失败 → 规则兜底（FALLBACK） | high | `AiCandidateServiceImpl.java:441-451`（首选失败且 service≠rule 时改调 ruleAiService 并标 FALLBACK） |
| 候选确认 → issue + feedback_issue_link | high | `AiCandidateServiceImpl.java:261-271` → `IssueService.confirmFromCandidate`；`IssueServiceImpl.java:25`（import AiCandidateIssueMapper，事务+乐观锁见注释与方法名） |
| 候选驳回 → 反馈回未归类 | high | `AiCandidateServiceImpl.java:282-311`（reject + resetFeedbackIfNoIssue → feedbackMapper.updateById:721） |
| 任务状态机 TODO→IN_PROGRESS→PENDING_VERIFY→DONE / WONT_DO | high | `enums/TaskStatus.java:40-52`（显式允许的后继集合，为结构+校验证据）；`TaskController.java:131` PUT `/{id}/status`（@PreAuthorize DEV,PM :130） |
| 任务流转 → task_event 追加 | high | `TaskController.java:185` GET `/{id}/events`；`sql/schema.sql:234`；README §九（append-only） |
| 反馈只读（无正文 update 接口） | high | FeedbackController 仅 POST 创建/批量、GET 查询、PUT `/{id}/status`（无正文修改端点）；README §二红线 |
| 优先级 PM 手选、AI 不输出 | high | `TaskController.java:152` PUT `/{id}/priority`（@PreAuthorize PM :151）；AI DTO 无优先级字段（`dto/AiCandidateSuggestion.java` 等）；README §二 |

## 权限矩阵（行为已证实部分）

| 端点组 | 角色（@PreAuthorize） | 证据行 |
|---|---|---|
| POST /api/feedbacks, /batch | CS, ADMIN | FeedbackController:60,75 |
| PUT /api/feedbacks/{id}/status | PM, ADMIN | FeedbackController:120 |
| /api/ai/** 生成/确认/驳回/起草/候选/日志 | PM, ADMIN | AiController:61-155 |
| GET /api/ai/status | ADMIN | AiController:171 |
| POST/PUT /api/issues 写路径 | PM, ADMIN | IssueController:60,103,122 |
| POST /api/tasks, priority, assignee | PM（assignee 亦 PM）| TaskController:74,151,167 |
| PUT /api/tasks/{id}/status | DEV, PM | TaskController:130 |
| GET /api/users | PM, ADMIN, DEV | UserController:43 |
| /api/auth/** , /uploads/** | permitAll | SecurityConfig:124-136 |

前端 `meta.roles` 与后端注解口径一致（router/index.js:42,60,103），最近一次提交 ad2c9ae 即为对齐修复。

## 未知项 / 待验证（列为验证任务，不作为已证实架构）

1. **运行时是否真实调用大模型**：代码支持，但依赖部署环境 `AI_PROVIDER/AI_API_KEY`；未在当前运行时观测（low → 需一次带 Key 的运行时验证或日志抽样）。
2. **OSS 实际可用性**：实现存在但默认关闭，未在真实 OSS 凭据下跑通（schema/代码证据，无运行证据）。
3. **单机部署拓扑**：README §5 为本机 Windows 方案；无多环境 IaC，生产拓扑仅此一版（medium）。
4. **feedback.status 完整状态机**：`FeedbackStatus` 枚举存在，但各状态间允许流转的约束强度未逐条核对（未展开，属 flow-visualizer 深入项）。
