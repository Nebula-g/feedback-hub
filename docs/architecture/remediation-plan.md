# Feedback Hub 整改计划（按优先级 + 验收标准）

> 配套 `architecture-review.md`（风险登记表）· 2026-11-02
> 排序依据：blast radius × 修复成本 × 是否影响答辩演示
> ⚠️ 本计划只做规划，未改动任何代码。P0 三项建议在任何"非本机演示"场景前完成。

## P0 · 当天可完成，成本低、收益最高（3 项）

### P0-1 JWT 密钥改为环境变量注入（对应 R-01，score 20）

- 改法：`application.yml:72` → `secret: ${JWT_SECRET:feedback-hub-dev-only-secret-change-me}`；
  `backend/run.sh` 与 `deploy/backend/start.bat` 内生成/注入一个随机密钥（沿用现有"清 SERVER__PORT"那段的做法）。
- 附带：`.gitignore` 加一层本地覆盖（如 `application-local.yml`），避免以后再提交明文。
- **验收**：① 不设环境变量能起来（演示不断链）；② 设了 `JWT_SECRET` 后，用旧密钥签发的 token 全部 401；③ `git grep` 查不到那串 demo secret。

### P0-2 修 `ai/logs` 权限口径（对应 R-03，score 10，已证实 bug）

- 两种口径二选一，**需要产品决定**（不是纯技术问题）：
  - A：DEV 应该能看 AI 调用记录 → 后端 `AiController.java:141,155` 改 `hasAnyRole('PM','ADMIN','DEV')`。
  - B：DEV 不该看 → 前端 `router/index.js:103` 去掉 `'DEV'`（菜单也不再出现）。
- 建议 A：`ai/logs` 是证据链回查，开发测试排查"AI 当时看到什么"有用；且 README §4.5 给 DEV 的权限描述本身没提这条，属未定义。
- **验收**：`dev01` 登录后点「AI 调用记录」→ 200 且有数据；`cs01` 登录 → 菜单不出现 + 直连 URL 得 403。

### P0-3 关掉生产噪声 + 收起文档端点（对应 R-05 + R-06，score 12+12）

- `application.yml:53` 的 `log-impl: StdOutImpl` 移入 dev profile（或改 `logging.level` 控制），使 SQL 不再全环境打印到控制台（含反馈原文参数）。
- `SecurityConfig.java:128-132` 的 swagger 白名单加 profile 条件（仅 dev permitAll，prod 需 ADMIN）。
- **验收**：prod profile 启动 → 控制台无 SQL 回显、`/swagger-ui.html` 未登录 401/403；dev profile 启动 → 两者照旧可用（答辩不受影响）。

## P1 · 回归保护（对应 R-02 score 20，簇 B 的根因）

按"最小有效"顺序补，不要一次铺开：

1. **权限矩阵测试**（最高性价比）：用已引入的 `spring-security-test`，一个 `@WebMvcTest`/`@AutoConfigureMockMvc` 类，把 `architecture-review.md` 的权限矩阵表逐条写成 `@Test` + `@WithMockUser(roles="DEV")` 断言 200/403。这一步会立刻拦住 R-03 这类漂移复发。
2. **AI 降级链路单测**：`RuleAiServiceImpl` 的 cluster/draft 纯函数测试 + `callWithFallback` 在模拟失败时返回 `status=FALLBACK`（把 `scripts/mock_ai_server.py` 的场景搬进 JUnit）。
3. **候选确认并发用例**：验证 `@Version` 那条 `updateById==0 → CONFLICT` 路径（QA-2 场景目前只有代码、没有自动化证明）。
4. **CI**：把 `scripts/run_all_tests.sh` + `mvn test` 挂进 `.github/workflows`。⚠️ 注意本机 git clone/push 通道受限，CI 落地需确认可达的托管端点，否则退化为本地一键脚本。
5. **脏库自愈**：`run_all_tests.sh` 末尾自动调 `reset_demo_data.sh`（对齐你偏好的一键化交付，避免手工漏步骤）。

- **验收**：`mvn test` 绿；故意把某个 `@PreAuthorize` 改错，测试应失败。

## P2 · 依赖与数据债（需评估窗口，别和演示撞车）

| 项 | 做法 | 风险与前置 |
|---|---|---|
| R-04 依赖老化 | Boot 3.2.5 → 3.2 线内最新 patch（小步、API 兼容），再单独评估 3.3/3.4 大版本；springdoc/MP/jjwt 同步 | Boot 大版本升级会牵动 Security 配置 DSL 与 `jakarta.*`，需一个独立验证窗口，**不要与 P0/P1 同批做** |
| R-08 JSON 列 | 新增 `ai_candidate_feedback_link` 关联表（对齐已有 `feedback_issue_link` 的做法），保留旧列过渡读 | 需 `sql/schema.sql` 变更 + 数据迁移；改后 R-07 的内存聚合可下推为 SQL |
| R-07 同步长任务 | 生成接口改异步任务：立即返回 `batchNo`，前端轮询/查看批次结果；LLM 超时与 HTTP 线程解耦 | 涉及前端交互改动（按钮态、轮询），是**产品可见变更**，需你确认交互再动 |
| R-09 可观测性 | 加 `spring-boot-starter-actuator`，仅暴露 `health`（ADMIN 或 localhost）；`start.bat` 启动后 curl health 判活 | 小改动，收益在"起没起来不用猜" |
| R-12 日志保留 | `ai_generation_log` 加保留策略（定期归档/清理，`task_event` 视审计要求保留） | 与 QA-5 追溯需求冲突，需先定保留期 |

## P3 · 真实上线才需要（README 已声明的边界，非缺陷）

- 单机 → 至少 DB 备份 + 应用可重启恢复演练；
- 本地盘 → `storage.type=oss` 真实凭据验证（`OssFileStorageService` 从未跑通真实 OSS，属未验证路径）；
- 大模型真实启用验证（`AI_PROVIDER=llm` 目前只有配置证据，无运行证据）；
- 登录频率限制（R-11）与 CSP（R-13）在有公网入口后再排。

## 建议的执行节奏

1. **P0 三项一次做完**（约 1 次会话），当场可用浏览器验收：旧 token 失效、DEV 能看日志、控制台安静。
2. P1 第 1 项（权限矩阵测试）单独做完 —— 它能防止本次这类 bug 再来。
3. P2 拆成两次独立变更：依赖升级 / 关联表重构，各自验证。
4. 每次结构性变更后，让 `architecture-health` 跑一次新鲜度核对（`docs/architecture/` 下 5 个模型文件需同步）。
