// Feedback Hub — 企业报销软件用户反馈与功能改进助手
// 当前态 C4 模型（system-modeler + c4model）
// 证据基线：2026-11-02 工作区代码；confidence 标注见 system-model.evidence.md
workspace "FeedbackHub-Architecture" "企业反馈归集与改进跟进系统 · 当前态模型" {

    !include https://d4m.marcus.io/diagrams/styles/c4-style-on-premise.txt

    model {
        // ── 人员（L1 参与者）──────────────────────────────
        cs = person "客服人员 (CS)" "录入/批量导入用户原始反馈" "角色证据：sys_user.role + @PreAuthorize('CS')，README 演示账号 cs01"
        pm = person "产品经理 (PM)" "触发 AI 归并、确认/驳回候选、建问题、建任务、定优先级、指派" "角色证据：@PreAuthorize('PM')，README pm01"
        dev = person "开发测试 (DEV)" "推进任务状态、填写验证结果、查看问题" "角色证据：@PreAuthorize('DEV')，README dev01"
        admin = person "系统管理员 (ADMIN)" "全部业务权限 + 查看 AI 运行状态" "SecurityConfig + @PreAuthorize('ADMIN')，README admin"

        // ── 系统边界 ────────────────────────────────────
        feedbackHub = softwareSystem "反馈归集与改进助手 (Feedback Hub)" "客服录入 → AI 辅助归并 → PM 确认 → 开发跟进 → 闭环可追溯" {

            spa = container "前端 SPA" "Vue 3 + Vite + Pinia + Naive UI；8 个业务视图 + 登录/403/404；路由级角色守卫" "Vue 3" {
                tags "internal"
            }
            nginx = container "Nginx" "静态资源托管 + /api 反向代理（开发期由 Vite proxy 等价承担）" "Nginx" {
                tags "internal"
            }
            api = container "后端 API (feedback-hub.jar)" "Spring Boot 3.2 单体：认证、反馈、AI 编排、问题、任务、文件、用户 7 组 Controller" "Spring Boot 3.2 / JDK 17 / MyBatis-Plus 3.5 / JJWT" {
                tags "internal"

                security = component "security 包" "JwtTokenProvider + JwtAuthenticationFilter + UserDetailsServiceImpl，签发/校验 JWT 并注入角色"
                controllers = component "controller 包" "Auth/Feedback/Ai/Issue/Task/File/User 共 7 个 REST 控制器，方法级 @PreAuthorize 鉴权"
                aiOrch = component "AiCandidateServiceImpl" "AI 编排层：选择 provider、失败降级到规则实现、全量写 ai_generation_log、候选确认/驳回"
                ruleAi = component "RuleAiServiceImpl" "关键词+模板的规则实现，零外部依赖，断网可用（默认实现与兜底）"
                llmAi = component "LlmAiServiceImpl" "OpenAI 兼容协议调用（JDK HttpClient），可配置 simulate-failure"
                storage = component "FileStorageService 抽象" "LocalFileStorageService（默认）/ OssFileStorageService，按 storage.type 条件装配"
                mappers = component "mapper 包" "8 个 MyBatis-Plus Mapper，分页插件 + @Version 乐观锁"
            }
            db = container "MySQL 8.0 (feedback_hub)" "8 张表：sys_user / feedback / ai_generation_log / ai_candidate_issue / issue / feedback_issue_link / improvement_task / task_event" "MySQL 8.0，本机端口 3308" {
                tags "database;internal"
            }
            uploads = container "本地上传目录" "截图等文件，url-prefix 暴露 /uploads/**" "文件系统" {
                tags "database;internal"
            }
        }

        // ── 外部系统 ────────────────────────────────────
        llm = softwareSystem "外部大模型 API" "OpenAI 兼容端点（默认配置指向 api.deepseek.com）。仅在 AI_PROVIDER=llm 且 Key 有效时被调用；失败自动降级。" "HTTP" {
            tags "external"
        }
        oss = softwareSystem "阿里云 OSS" "storage.type=oss 时启用的备选文件存储，当前默认未启用。" "SDK/HTTP" {
            tags "external;lowConfidence"
        }

        // ── 关系（L1）──────────────────────────────────
        cs -> feedbackHub "录入/批量导入反馈"
        pm -> feedbackHub "触发 AI 归并、确认/驳回候选、建问题与任务"
        dev -> feedbackHub "推进任务状态、验证结果"
        admin -> feedbackHub "全部操作 + 查看 AI 调用日志与状态"

        // ── 关系（L2 容器级）────────────────────────────
        cs -> spa "浏览器访问"
        pm -> spa "浏览器访问"
        dev -> spa "浏览器访问"
        admin -> spa "浏览器访问"
        spa -> nginx "加载静态页面 / 调用 /api（开发期经 Vite proxy）"
        nginx -> api "反向代理 /api/ → 127.0.0.1:8081（proxy_pass 不带斜杠）"
        api -> db "JDBC/MyBatis-Plus 读写 8 张表"
        api -> uploads "写入/读取截图文件"
        api -> llm "AI_PROVIDER=llm 时 HTTP 调用；超时/异常/垃圾返回均降级" "external"
        api -> oss "备选：图片存储切换" "external"

        // ── 关系（L3 组件级，后端单体内部）────────────────
        controllers -> security "每请求校验 JWT 与角色"
        controllers -> aiOrch "/api/ai/** 的全部 8 个端点"
        controllers -> mappers "反馈/问题/任务/用户 CRUD 路径"
        aiOrch -> ruleAi "默认路径 + 失败兜底（status=FALLBACK 落日志）"
        aiOrch -> llmAi "wantLlm 时首选调用"
        llmAi -> llm "OpenAI 兼容 HTTP 调用"
        aiOrch -> mappers "写 ai_candidate_issue / ai_generation_log；确认时事务内写 issue + feedback_issue_link"
        controllers -> storage "文件上传经 FileController"
        storage -> uploads "local 实现落盘"
    }

    views {
        systemLandscape "L0-L1-Context" {
            include *
            autoLayout
            title "系统上下文：4 类角色 · 1 个内部系统 · 2 个外部系统"
        }

        container "L2-Containers" {
            group feedbackHub "Feedback Hub 系统边界" {
                spa nginx api db uploads
            }
            include *
            autoLayout
            title "容器视图：SPA + Nginx + Spring Boot 单体 + MySQL + 本地存储（外部 LLM/OSS 虚线边界）"
        }

        component api "L3-Backend-Components" {
            include *
            autoLayout
            title "后端单体组件视图：AI 双实现与降级编排"
        }

        styles {
            element "Person" {
                shape person
                background #08427b
                font color #ffffff
            }
            element "Spring Boot" {
                background #6db33f
                font color #242424
            }
            element "Vue 3" {
                background #42b883
                font color #242424
            }
            element "Nginx" {
                background #009639
                font color #ffffff
            }
            element "MySQL 8.0，本机端口 3308" {
                shape cylinder
                background #f29111
                font color #242424
            }
            element "文件系统" {
                shape folder
                background #b0b0b0
                font color #242424
            }
            element "external" {
                background #999999
                color #ffffff
                strokeWidth 1
            }
            element "lowConfidence" {
                border dashed
                opacity 60
            }
        }
    }
}
