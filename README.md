# 企业报销软件用户反馈与功能改进助手

> AI 全栈课程贯穿式大作业 · **挑战选题二**
> 一套给「报销软件公司」内部用的反馈归集与改进跟进系统：
> 客服录入 → AI 辅助归并 → 产品经理确认 → 开发测试跟进 → 闭环可追溯。

---

## 一、这个项目解决什么问题

报销软件公司每周会收到大量用户反馈，这些反馈的**说法千差万别，但指向的问题往往重复**：

```
"发票上传总失败"        ┐
"传发票一直报错"        ├─► 其实是同一个问题
"上传发票的时候卡住了"  ┘
```

靠人工逐条看、凭印象归并，会出现两个典型问题：

1. **漏掉真问题** —— 散落在几十条反馈里的共性问题，没有被归到一起；
2. **被声量带偏** —— 谁催得急就先做谁，而不是看影响面。

本系统把这条链路固化下来，并让 **AI 只做"客观整理"，决策权始终在人手里**。

---

## 二、核心设计：AI 的三条红线

这是整个项目最关键的设计约束，也是区别于"直接调 API 生成内容"的地方：

| 红线 | 具体体现 |
|------|---------|
| **草稿与事实物理分离** | AI 产出写入 `ai_candidate_issue`（草稿表），PM 确认后才生成 `issue`（正式表） |
| **AI 不输出优先级** | AI 只给客观依据（反馈条数、涉及客户数、是否阻塞主流程），优先级由 PM 手选 |
| **原始反馈只读** | 反馈表不提供任何修改正文的接口，AI 也不修改原始数据 |
| **AI 挂了业务不断** | 大模型调用失败自动降级为本地规则实现，流程照常走完 |

此外，**每次 AI 调用全量落库**（输入快照、返回原文、耗时、状态），构成可回查的证据链 ——
事后任何人质疑"这条结论是不是 AI 编的"，都能翻日志核对。

---

## 三、技术栈

| 层 | 选型 | 说明 |
|---|------|------|
| 后端 | Spring Boot 3.2 + Spring MVC | Boot 已内建 MVC，不并列写 |
| 安全 | Spring Security + JJWT 0.12 | Boot 3 用 `jakarta.*` 包名 |
| ORM | MyBatis-Plus 3.5 | 分页插件 + 乐观锁 `@Version` |
| 数据库 | MySQL 8.0 | 本机端口 3308 |
| 文件 | `FileStorageService` 抽象 | local（默认）/ OSS 双实现 |
| 文档 | springdoc-openapi 2.x | springfox 不兼容 Boot 3 |
| 前端 | Vue 3 + Vite + Pinia + Vue Router | |
| UI | Naive UI | 深色主题，参考 Linear 设计系统 |
| 部署 | Nginx（静态资源 + `/api` 反代） | |

---

## 四、快速开始（开发模式）

### 4.1 前置条件

- JDK 17
- Maven 3.9+
- MySQL 8.0（本项目本机端口为 **3308**，请按实际调整）
- Node.js 18+

### 4.2 初始化数据库

```bash
# 建库 + 建表（8 张表）
mysql -uroot -proot -P3308 < sql/schema.sql

# 演示数据（4 个账号 + 60 条脱敏反馈 + 历史问题/任务）
mysql -uroot -proot -P3308 feedback_hub < sql/init-data.sql
```

### 4.3 启动后端

```bash
cd backend
bash run.sh          # 监听 8081
```

> **为什么必须用 `run.sh` 而不是直接 `mvn spring-boot:run`？**
> 某些环境会注入 `SERVER__PORT=0` 这类环境变量，
> Spring Boot 的宽松绑定会把它映射成 `server.port`，导致忽略配置的 8081
> 而随机选端口。`run.sh` 会先清除这些变量再启动。

### 4.4 启动前端

```bash
cd frontend
npm install
npm run dev          # 监听 5173
```

浏览器访问 http://localhost:5173

### 4.5 演示账号

| 账号 | 密码 | 角色 | 主要权限 |
|------|------|------|---------|
| `cs01` | `cs123456` | 客服人员 | 录入反馈、批量导入 |
| `pm01` | `pm123456` | 产品经理 | 触发 AI、确认/驳回候选、建问题、建任务、定优先级、指派 |
| `dev01` | `dev123456` | 开发测试 | 推进任务状态、填写验证结果 |
| `admin` | `admin123456` | 系统管理员 | 全部业务权限 + 查看 AI 运行状态 |

---

## 五、部署（生产模式 · 本机 Windows + Nginx）

### 5.1 打包

```bash
# 后端
cd backend
bash mvn.sh -o clean package -DskipTests
# 产物：target/feedback-hub.jar

# 前端
cd frontend
npm run build
# 产物：dist/
```

### 5.2 目录规划

```
D:\feedback-deploy\
├── feedback-backend.jar          # 后端 jar
├── logs\
│   └── backend.log
└── uploads\                       # 上传的图片

<nginx>\html\feedback-ui\          # 前端 dist 内容
```

### 5.3 Nginx 配置

复制 `deploy/nginx/feedback.conf` 到 `nginx/conf/conf.d/`，
按需修改 `root` 与 `alias` 的实际路径，然后：

```bash
nginx -s reload
```

配置要点（踩坑记录）：

```nginx
# ✅ 正确：proxy_pass 结尾不带斜杠，保留 /api 前缀
location /api/ {
    proxy_pass http://127.0.0.1:8081;
}

# ❌ 错误：带斜杠会把 /api/feedbacks 改写成 /feedbacks，后端全部 404
# proxy_pass http://127.0.0.1:8081/;
```

```nginx
# SPA 路由回退：直接访问 /issues/3 时必须回退到 index.html
try_files $uri $uri/ /index.html;
```

### 5.4 启动 / 停止后端

```bat
deploy\backend\start.bat    :: 启动（最小化窗口，日志写入 logs\backend.log）
deploy\backend\stop.bat     :: 停止（按命令行精确匹配，不误杀其他 java 进程）
```

启动后访问 http://localhost:8080

---

## 六、AI 模块：双实现与降级

### 6.1 两个实现

| 实现 | 特点 | 何时用 |
|------|------|--------|
| `RuleAiServiceImpl` | 关键词 + 模板，零外部依赖，**断网可用** | 默认；以及大模型失败时的兜底 |
| `LlmAiServiceImpl` | OpenAI 兼容协议，JDK 自带 HttpClient | 配置了有效 Key 时 |

> **实现顺序是先规则、后大模型**。规则实现是系统的"地板"——
> 保证任何情况下业务链路都能走完，这也是验收场景 8 的要求。

### 6.2 切换到真实大模型

```bash
export AI_PROVIDER=llm
export AI_API_KEY=sk-xxxxxxxxxxxx      # 密钥走环境变量，不进代码库
export AI_BASE_URL=https://api.deepseek.com
export AI_MODEL=deepseek-chat
bash run.sh
```

不配置或调用失败时，自动回落到规则实现，并在日志里留下 `status=FALLBACK`。

### 6.3 三种故障场景的实测结果

| 故障 | 模拟方式 | 结果 |
|------|---------|------|
| 服务不可用 | `AI_SIMULATE_FAILURE=true` | HTTP 200，`provider=RULE`，`fallback=true` |
| 请求超时 | 调小 `AI_TIMEOUT_MS` | 日志 `status=TIMEOUT`，耗时 30000ms |
| **返回垃圾数据** | `scripts/mock_ai_server.py` 返回非 JSON | HTTP 200，`provider=RULE`，日志 `error_msg` 记录"无法解析为 JSON" |

### 6.4 演示用的「模拟 AI 故障」开关

```bash
AI_SIMULATE_FAILURE=true bash run.sh
```

开启后所有 AI 调用强制失败，用于当场演示"AI 挂了但业务照常走完"。

---

## 七、测试

### 7.1 测试脚本

| 脚本 | 用途 | 断言数 |
|------|------|--------|
| `scripts/smoke_test.py` | 主链路冒烟（登录→录入→归类→建任务→流转） | 24 |
| `scripts/smoke_test_ai.py` | AI 模块冒烟（提取→聚类→起草→确认→留证） | 39 |
| `scripts/contract_check.py` | 前端↔接口↔实体三方契约核对 | 26 |
| `scripts/test_robustness.py` | 异常 / 边界 / 并发 / 越权 | 25 |
| `scripts/mock_ai_server.py` | 模拟"返回垃圾数据"的大模型端点 | — |

### 7.2 运行方式

```bash
# 确保后端已启动
python scripts/smoke_test.py
python scripts/smoke_test_ai.py
python scripts/contract_check.py
python scripts/test_robustness.py
```

> ⚠️ 跑完 `test_robustness.py` 后数据库会残留测试数据
> （批量导入的 200 条等），需执行一次重置。

### 7.3 数据恢复

```bash
bash scripts/reset_demo_data.sh
```

恢复后状态：60 条反馈（58 条待归类）/ 3 个问题 / 4 个任务 / 0 张候选卡片 / 4 条 AI 日志。

详细测试报告见 `docs/07-test-report.md`。

---

## 八、目录结构

```
Company/
├── ACTION-PLAN.md              # 六阶段行动计划
├── README.md                   # 本文件
├── docs/
│   ├── 01-project-direction-card.md    # 第一章 方向卡
│   ├── 02-requirements.md              # 第二章 需求说明
│   ├── 03-design-pages.md              # 第三章 页面与工作台设计
│   ├── 04-design-database.md           # 第四章 数据库设计
│   ├── 05-api-list.md                  # 第五章 接口清单
│   ├── 06-ai-design.md                 # 第六章 AI 模块设计
│   ├── 07-test-report.md               # 第七章 测试报告
│   └── 08-demo-script.md              # 演示脚本
├── sql/
│   ├── schema.sql              # 建表脚本（8 张表）
│   └── init-data.sql           # 演示数据
├── backend/                    # Spring Boot 后端
│   ├── mvn.sh                  # Maven 包装脚本（绕过坏掉的 MAVEN_HOME）
│   ├── run.sh                  # 开发启动脚本
│   └── src/main/java/com/company/feedback/
│       ├── config/             # 配置类
│       ├── controller/         # 7 个 Controller
│       ├── dto/                # 请求/传输对象
│       ├── entity/             # 8 个实体
│       ├── enums/              # 11 个枚举
│       ├── exception/          # 异常处理
│       ├── mapper/             # MyBatis Mapper
│       ├── security/           # 认证与鉴权
│       ├── service/            # 业务层（含 AI 双实现）
│       └── vo/                 # 视图对象
├── frontend/                   # Vue 3 前端
│   └── src/
│       ├── api/                # 接口封装
│       ├── views/              # 12 个页面
│       ├── router/             # 路由与守卫
│       ├── stores/             # Pinia 状态
│       └── constants/          # 枚举常量
├── deploy/
│   ├── nginx/feedback.conf     # Nginx 配置
│   └── backend/                # Windows 启停脚本
└── scripts/                    # 测试与运维脚本
```

---

## 九、数据库表（8 张）

| 表 | 用途 |
|----|------|
| `sys_user` | 用户与角色 |
| `feedback` | 原始反馈（**只读**，无 update 接口） |
| `ai_generation_log` | AI 调用日志（**append-only**） |
| `ai_candidate_issue` | AI 候选问题卡片（草稿） |
| `issue` | 正式问题 |
| `feedback_issue_link` | 反馈与问题的多对多关联 |
| `improvement_task` | 改进任务 |
| `task_event` | 任务流转时间线（**append-only**） |

---

## 十、已知限制

诚实交代当前方案的边界，这些**不属于缺陷**，是能力范围的有意界定：

1. **规则实现的语义理解有限**
   只能识别预设的关键词与句式，对全新表述、反讽语气、隐含诉求无能为力。
   详细的四个局限与改进方向见 `docs/06-ai-design.md`。

2. **聚类不跨模块**
   即便两条反馈描述相似，只要属于不同功能模块就不合并。
   这是**有意为之**的硬约束 —— 跨模块合并会让责任人和验收标准变得模糊，
   宁可多出几张卡片让人判断。

3. **单机部署**
   当前为本机 Windows + Nginx 方案，未做负载均衡、未做水平扩展。

4. **图片默认存本地磁盘**
   演示环境下更稳定；生产环境可通过一行配置切换到 OSS。
   抽象层 `FileStorageService` 已就绪。

5. **测试脚本未接入 CI**
   测试为独立 Python 脚本，需手动执行；跑完注意恢复演示数据。

6. **演示数据均为虚构**
   60 条反馈是脱敏虚构数据，不含任何真实客户信息，
   **不代表任何真实企业的实际调研结论**。

---

## 十一、文档索引

| 章节 | 文档 | 内容 |
|------|------|------|
| 第一章 | `docs/01-project-direction-card.md` | 项目方向卡 |
| 第二章 | `docs/02-requirements.md` | 需求说明 |
| 第三章 | `docs/03-design-pages.md` | 页面与岗位工作台设计 |
| 第四章 | `docs/04-design-database.md` | 数据库设计 |
| 第五章 | `docs/05-api-list.md` | 接口清单与权限矩阵 |
| 第六章 | `docs/06-ai-design.md` | AI 模块设计（重点） |
| 第七章 | `docs/07-test-report.md` | 测试报告 |
| — | `docs/08-demo-script.md` | 9 步演示脚本 |

---

## 十二、常用命令速查

```bash
# ---------- 开发 ----------
cd backend  && bash run.sh        # 启动后端（8081）
cd frontend && npm run dev        # 启动前端（5173）

# ---------- 构建 ----------
cd backend  && bash mvn.sh -o clean package -DskipTests
cd frontend && npm run build

# ---------- 测试 ----------
python scripts/smoke_test.py
python scripts/smoke_test_ai.py
python scripts/contract_check.py
python scripts/test_robustness.py

# ---------- 数据 ----------
bash scripts/reset_demo_data.sh                                  # 恢复演示数据
mysql -uroot -proot -P3308 feedback_hub -e "SELECT ..."          # 直接查库

# ---------- AI 模式 ----------
AI_PROVIDER=llm AI_API_KEY=sk-xxx bash backend/run.sh            # 真实大模型
AI_SIMULATE_FAILURE=true bash backend/run.sh                     # 演示故障降级
python scripts/mock_ai_server.py 8099                            # 模拟垃圾数据
```
