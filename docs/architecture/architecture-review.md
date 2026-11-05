# Feedback Hub 架构评审（风险 + 技术债 + 质量属性）

> 由 `risk-quality-reviewer` 生成 · 2026-11-02 · 基线 commit `ad2c9ae` + 当前工作区
> 前置模型：`system-model.structurizr.dsl` / `business-architecture.md`（同目录）
> 评分：`score = probability(1-5) × impact(1-5)`；≥16 高，8–15 中，<8 低
> 关联产物：`risk-map.dot`（风险簇图）、`remediation-plan.md`（整改顺序与验收）

## 0. 先说结论

这套系统**设计意图清晰、核心链路自洽**（草稿/事实分离、降级兜底、全量留证都有代码级证据，不是只写在文档里）。改进空间集中在**三件事上**：

1. **上线前的安全门槛没过**：JWT 密钥明文提交、Swagger 无鉴权全开、SQL 带参数打印到控制台、依赖 2 年多未升级。
2. **回归保护是空的**：`backend/src/test` 目录根本不存在，pom 里引了 `spring-boot-starter-test` 和 `spring-security-test` 但没有一个测试类；现有 4 个 Python 脚本能力不弱，但都在服务外部、且未接 CI。
3. **一个已证实的权限口径 bug**：`ai/logs` 前端放行 DEV、后端只允许 PM/ADMIN —— 而 `ad2c9ae` 这次提交正是为了对齐口径，说明这条没对齐干净。

同时必须诚实区分：README §十已经**主动声明的边界**（单机部署、规则语义有限、聚类不跨模块、图片默认本地、测试未接 CI）不属于缺陷，本次不再重复计为风险，只在"上线才需要处理"的表里列一次。

## 1. 风险登记表（按 score 降序）

| ID | 风险 | 影响的质量属性 | P | I | Score | 证据 |
|---|---|---|---|---|---|---|
| R-01 | JWT 签名密钥为明文常量且已提交进仓库，无环境变量覆盖 | 安全性 / 可维护性 | 4 | 5 | **20** | `application.yml` 中的字面量密钥（已脱敏：改为 `${JWT_SECRET:...}` 环境变量注入）；对比同文件 `api-key: ${AI_API_KEY:...}` 才是有覆盖的写法 |
| R-02 | 后端测试为零（`src/test` 目录不存在） | 可维护性 / 可测试性 / 交付速度 | 5 | 4 | **20** | `ls backend/src/ → main`；`pom.xml:137,142` 引入 test starter 但无测试类 |
| R-03 | `ai/logs` 前后端权限口径不一致，DEV 点菜单必得 403 | 正确性 / 用户体验 | 5 | 2 | **10** | 前端 `router/index.js:103` `roles:['PM','ADMIN','DEV']` vs 后端 `AiController.java:141` `hasAnyRole('PM','ADMIN')` |
| R-04 | 依赖整体停留在 2024 年（Boot 3.2.5 / MP 3.5.7 / springdoc 2.5.0 / jjwt 0.12.5 / lombok 1.18.32 / oss 3.17.4） | 安全性 / 可维护性 | 4 | 4 | **16** | `pom.xml:15,32-37` |
| R-05 | MyBatis SQL 日志用 `StdOutImpl` 全环境打印（含绑定参数） | 性能 / 安全(数据入日志) | 4 | 3 | **12** | `application.yml:53` `log-impl: ...StdOutImpl` |
| R-06 | Swagger + `/v3/api-docs` 无条件 `permitAll`，接口面全暴露 | 安全性 | 3 | 4 | **12** | `SecurityConfig.java:128-132` |
| R-07 | AI 生成为单请求同步长任务：30s 模型超时 + 全量载入待归类池 + 逐条落库 | 性能 / 可扩展性 | 3 | 4 | **12** | `application.yml:129` `timeout-ms: 30000`；`AiCandidateServiceImpl.java:108-165` 选材→调用→落库串在一次请求内 |
| R-08 | `related_feedback_ids` 以 JSON 字符串存列，占用判定需全表捞候选+内存解析 JSON | 数据模型债 / 可扩展性 | 4 | 3 | **12** | `schema.sql:114-133`；`AiCandidateServiceImpl.java:565-580` 全量 `selectList` 后 `JsonUtil.jsonToLongList` 聚集合 |
| R-09 | 无 actuator / 健康检查 / 指标，`start.bat` 起完不做存活判定 | 可观测性 / 运维 | 4 | 3 | **12** | grep `actuator` 在 pom 与 yml 均无命中；`deploy/backend/start.bat` |
| R-10 | 4 个 Python 测试脚本未接 CI，且 `test_robustness.py` 跑完脏库需人工 reset | 交付速度 / 可测试性 | 4 | 3 | **12** | README §七；`.github/workflows` 不存在；`scripts/run_all_tests.sh` |
| R-11 | 登录接口无频率限制/失败锁定（permitAll + BCrypt 重计算） | 安全性 | 3 | 3 | **9** | `SecurityConfig.java:124` `/api/auth/**` permitAll；无 bucket4j/resilience4j 依赖 |
| R-12 | `ai_generation_log`/`task_event` append-only 但无保留期与清理，快照含反馈原文 | 隐私/合规 / 成本 | 3 | 3 | **9** | `schema.sql:85,234`；`buildInputSnapshot` 写入 `content` 原文（`AiCandidateServiceImpl.java:595-600`） |
| R-13 | CORS 白名单写死 localhost、无 CSP、token 存 localStorage | 安全性 / 可部署性 | 3 | 3 | **9** | `SecurityConfig.java:173-178`；`frontend/index.html` 无 CSP；`stores/user.js:8-19`（代码自己已声明这是权衡） |
| R-14 | `spring.profiles.active: dev` 写死在基础配置，且 `application-dev.yml` 是 0 字节空文件却被 git 跟踪 | 配置卫生 | 3 | 2 | **6** | `git diff application.yml`（新增 profiles 段）；`ls -la resources` → size 0；`git ls-files` 含该文件 |
| R-15 | MySQL 口令 + 4 个演示账号口令明文入库入文档 | 安全性（演示数据，影响有限） | 4 | 2 | **8** | `application.yml` 曾含真实口令（已脱敏：改为环境变量注入 + 本地 gitignore 文件）；README §4.5 |
| R-16 | `FileController` 无任何 `@PreAuthorize`，任意登录角色可上传 | 安全性（最小权限） | 3 | 2 | **6** | `FileController.java:51` 仅方法注解缺失；正向证据：`AbstractFileStorageService.java:30-34` 有扩展名白名单+10MB 上限 |
| R-17 | 仓库根遗留临时文件未忽略，`deploy/mysql/` 新目录未提交 | 工程卫生 | 3 | 1 | **3** | `git status`：`?? .tmp-ps-test.txt`、`?? .tmp-svc.txt`、`?? deploy/mysql/` |

## 2. 三个高优先级簇（为什么它们排在前面）

### 簇 A：安全边界与它的"不一致"（R-01 · R-06 · R-11 · R-13 · R-15）

这一簇的共性不是"缺安全设计"，而是**同一个项目里安全标准不统一**：
AI Key 和 OSS 凭据都走了 `${ENV:default}` 的正确写法（`application.yml:102,125`），而**最关键、能直接换掉整个身份体系的 JWT 密钥反而是明文常量**。同理，文件上传做了扩展名白名单和大小限制（考虑得很细），但 API 文档端点完全敞开。

影响路径：密钥泄露 → 任何人可签发 `ROLE_ADMIN` 的 token → 全部 `@PreAuthorize` 与前端路由守卫同时失效。这会把本项目最有价值的两个设计（角色边界 + AI 红线）一次性抹掉。
置信度：**high**（直接配置证据）。演示/答辩环境下不致命，**任何非本机暴露都致命**。

### 簇 B：回归保护缺失（R-02 · R-03 · R-10）

`R-03` 是这条簇存在的**后果证据**，不是独立小 bug：`ad2c9ae` 提交信息写着"AI候选与问题接口加入ADMIN角色，与前端口径对齐"，改完后 `ai/logs` 的 DEV 仍然口径不一致 —— 有 Java 层权限测试的话，这类漂移会在提交时被拦住。
现有 Python 脚本（24+39+26+25=114 条断言）覆盖了主链路和越权，**能力其实不弱**，缺的是：① 不在 CI 里，靠人跑；② 需要服务已启动；③ 跑完污染数据库。
置信度：**high**。

### 簇 C：数据/性能债在规模变大时才咬人（R-07 · R-08 · R-09 · R-12）

当前演示数据 60 条反馈，这些问题**完全看不出来**（`idx_module_status`、`idx_status_priority` 等索引其实建得不错）。但结构上有一条明确的成长痛：

- `R-08` 是最需要优先修的根因：`related_feedback_ids` 存 JSON，导致"这张卡片占了哪些反馈"无法用 SQL 查，只能每次捞全部候选到内存解析。它同时使 `R-07` 的生成接口随候选数线性变慢。
- `R-07` 的同步长请求在真实模型 + 池子变大时，会先撞浏览器/Nginx 超时，再撞线程占用。
- `R-05`（SQL 打印）和 `R-12`（日志无保留）会在数据量上来后同时放大：日志体积与原文数据驻留。

置信度：**medium-high**（代码路径已证实，规模影响为推断 —— 需要一次带量压测才能定量）。

## 3. 质量属性场景（要测就测这几个）

| 场景 | 刺激 | 期望响应 | 当前是否可测 |
|---|---|---|---|
| QA-1 降级地板 | LLM 端点返回非 JSON | HTTP 200、`provider=RULE`、`fallback=true`、日志 `status=FALLBACK`+error_msg | ✅ 已被 `mock_ai_server.py` 覆盖（README §6.3 有实测） |
| QA-2 并发确认 | 两个 PM 同时 confirm 同一候选 | 一成功一 409，不产生两张重复 issue | ⚠️ 代码有 `@Version`+`updateById==0→CONFLICT`（`AiCandidateServiceImpl.java:302`），但**无自动化验证** |
| QA-3 权限边界 | DEV 访问各 AI 端点 | 403 且前端不给入口 | ❌ 正是 R-03 破口 |
| QA-4 量级扩展 | 待归类反馈从 60 → 5000 条 | 生成接口不超时、内存不爆 | ❌ 无压测、无指标（R-09） |
| QA-5 数据可追溯 | 事后质询某条 issue 来源 | 由 `issue_no` 反查候选卡片 → `batch_no` → AI 日志快照 | ✅ 表结构支持（`idx_batch_no`）；⚠️ 无保留期，日志可能被清 |

## 4. 明确不作为风险列出的项（README 已声明的边界）

单机部署无 HA、规则实现语义能力有限、聚类不跨模块、图片默认本地盘、演示数据虚构 —— 这些是**能力范围的有意界定**，作者已在 README §十逐条交代。只在"若要真实上线"清单里以工作量形式出现（见 `remediation-plan.md` P3）。

## 5. 阅读与复核顺序

1. 先看 `remediation-plan.md` 的 P0（3 项，都是当天能完成的）。
2. `risk-map.dot` 用 Qoder 的 DOT 预览打开，看簇与影响路径。
3. 复核任一结论时，`risk-map.dot` 节点内标注的文件:行号即入口。
