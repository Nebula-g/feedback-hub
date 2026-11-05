# AI 功能验收测试报告（2026-11-04）

> 测试方式：接口级实测（curl）+ 数据库核对（MySQL 3308 / feedback_hub）+ 代码核查（Spring Boot 后端）。
> 测试环境：本地 dev，AI provider = rule（本地规则引擎，LLM 未配 key），fallback 开启。
> 测试账号：cs01(CS) / pm01(PM) / dev01(DEV) / admin(ADMIN)。
> 测试数据：批量导入反馈 #74–#79（多种「发票上传失败」说法 + 复合反馈 + 语义模糊反馈）。

## 结论总览

| # | 验收点 | 结论 |
|---|--------|------|
| 1 | 多条不同说法 → AI 生成候选并保留原文 | ✅ 通过 |
| 2 | 复合反馈拆成多个候选问题 | ✅ 通过 |
| 3 | 含义不同、关键词相似 → 不经 PM 确认不成为候选问题 | ⚠️ 基本通过（1 个规则引擎误归组瑕疵，见 3.3） |
| 4 | PM 可合并、修改、驳回 AI 候选 | ⚠️ 部分通过（**合并无接口**，见 4.4） |
| 5 | AI 需求草稿须经 PM 确认才能建任务 | ✅ 通过 |
| 6 | 开发/测试可更新任务并记录验证结果 | ✅ 通过 |
| 7 | 普通客服不能定优先级、不能删原始反馈 | ✅ 通过 |
| 8 | 模型调用失败时仍可手工分类与建任务 | ✅ 通过 |

## 1. 多种说法 → 候选 + 原文保留 ✅

- `POST /api/feedbacks/batch`（CS）一次导入 6 条成功（ID 74–79），状态均为 NEW。
- 「发票传不上去，一直转圈最后提示上传失败」(#74) 与「点击上传发票按钮没反应，反复重试也不行」(#75)
  归并到同一候选卡片 #58（置信度 0.95 = 多条叠加）。
- **原文保留三重证据**：
  1. `feedback.content` 全程未被改写（AI 无任何 setContent 路径，代码核查确认）；
  2. 候选卡片经 `related_feedback_ids` 回链原始反馈；
  3. 需求草稿（`POST /api/ai/issues/{id}/draft`）原文摘录：草稿中逐字引用了 3 条原始反馈。

## 2. 复合反馈拆分 ✅

- #76「发票上传失败，**而且**看不到退回原因」→ 拆成 2 张卡：
  #58（INVOICE_UPLOAD·上传失败）+ #59（RETURN_MODIFY·退回原因不明，置信度 0.65）。
- #79「报销单提交不了，而且审批进度一直不更新」→ 同样拆出独立卡片 #62（审批进度）。
- 规则引擎按并列连词（而且/另外/同时…）切分子句，支持跨模块。

## 3. 关键词相似 ≠ 自动成候选 ⚠️

**通过的部分：**
- 「发票上传**成功**但查不到记录」(#77) 与「上传**失败**」关键词高度相似，规则引擎判定现象不同，
  未并入 #58，独立成卡 #60。LLM 模式的 prompt 亦有同款约束（仅关键词相似但现象不同必须拆卡）。
- 「系统很卡」→ 依据不足独立卡 #61（置信度 0.25，reason 写明「未做自动合并，需人工判断」）。
- **所有候选一律 `status=DRAFT`，`generated_issue_id` 全部为 NULL**——不经 PM 确认，
  不会产生任何正式问题（generate 前后 issue 表无新增）。

**瑕疵（3.3）：** #79 的子句「报销单**提交不了**提示网络错误」被跨模块关键词规则误命中，
并入了「发票上传失败」卡片 #58（related_feedback_ids 混入）。这是规则引擎已知局限
（代码注释自认「认不出『传不上去』和『无法提交』是一回事」，反之亦然）。但后果可控：
仍是 DRAFT，PM 可通过拆分接口修正（本次实测已用 `POST /api/ai/candidates/58/split` 拆出）。

## 4. PM 合并 / 修改 / 驳回 ⚠️

| 操作 | 接口 | 实测结果 |
|------|------|----------|
| 修改后确认 | `POST /api/ai/candidates/60/confirm`（改标题/描述） | ✅ 状态记 **MODIFIED**，issue 落库，AI 原稿快照写入 `issue.ai_draft_content` |
| 原样确认 | `POST /api/ai/candidates/63/confirm`（与 AI 原稿逐字一致） | ✅ 状态记 **CONFIRMED**（issue 10），关联反馈 #74–76 转 LINKED |
| 驳回 | `POST /api/ai/candidates/61/reject`（reason 必填） | ✅ 状态记 **REJECTED**，关联反馈 #78 回退 NEW |
| 拆分 | `POST /api/ai/candidates/58/split`（2 parts） | ✅ 原卡转 SPLITTED，新卡 #63/#64 DRAFT、置信度清空 |
| **合并** | — | ❌ **无接口**。`CandidateStatus.MERGED` 只是枚举值，Controller/Service 无任何入口；
     `POST /api/ai/candidates/64/merge` 返回 500（无此路由）。替代方案是把反馈手工关联到已有
     issue（`POST /api/issues/{id}/feedbacks`，实测可用），但候选卡片本身无法标记为「已合并」 |

## 5. 草稿须经确认才能建任务 ✅

- 候选必须经 PM `confirm` 才生成 issue → 只有拿到 issueId 才能建任务（唯一建任务入口 `POST /api/tasks`）。
- 建任务权限 `hasRole('PM')`：DEV 403 ✓、**ADMIN 也被禁** 403 ✓、PM 成功（task #5，P1）。
- `POST /api/ai/issues/10/draft` 只返回草稿文本（含原始反馈摘录），不直接落任务。

## 6. 开发/测试更新任务 + 记录验证结果 ✅

角色说明：系统无独立 TEST 角色，开发测试合为 `DEV`（RoleEnum.label=「开发测试」）。

实测 task #5 全流程：TODO→DONE 跳步被拒（409）→ IN_PROGRESS → PENDING_VERIFY →
打回 IN_PROGRESS（不带 remark 被拒 400「必须填写说明」；带「验证不通过：大文件上传仍超时」成功）
→ 再 PENDING_VERIFY → **DONE**（remark=复测通过）。
- 时间线 `GET /api/tasks/5/events` 完整记录每次流转（操作人/角色/时间/remark）。
- DONE 自动把关联反馈 LINKED→CLOSED（#74–76 实测转 CLOSED）。

## 7. 客服权限限制 ✅

| 越权操作 | 结果 |
|----------|------|
| CS 设任务优先级 | 403 |
| DEV 设任务优先级（仅 PM 可设） | 403 |
| CS 改反馈状态 | 403 |
| 删除反馈（DELETE /api/feedbacks/{id}） | 400「请求方法不支持」——**接口根本不存在**，原始反馈设计上只读 |
| CS 确认/驳回 AI 候选 | 403 |

## 8. AI 失败时的手工兜底 ✅

以 `AI_PROVIDER=llm AI_SIMULATE_FAILURE=true AI_FALLBACK_ENABLED=false` 重启后端强制复现：

1. `POST /api/ai/candidates/generate` → **503**，message 明确提示
   「已切换为手工模式。你可以跳过 AI，直接在『问题列表』中手工新建问题」。
2. 失败留档：`GET /api/ai/logs?status=FAILED` 有记录（provider=LLM、含输入快照）。
3. 手工链路全部可用：
   - PM 手工建 issue（#11，sourceType=MANUAL，无 AI 原稿）✅
   - 手工关联反馈 #77/#78（NEW→LINKED，linkType=PM）✅
   - 基于手工 issue 建任务（task #6）✅
   - 手工判无效反馈 #79（IGNORED + remark）✅
4. 另一条降级路径（fallback 开启时）：LLM 失败自动切规则引擎，日志记 FALLBACK，用户无感。

## 测试后环境状态

- 后端已恢复正常配置（provider=rule，无 simulate-failure），前端 dev 5173、DB 3308 未动。
- 测试产生的数据留在库中：反馈 #74–79、候选 #58–#64、issue #8–#11、task #5/#6，便于前端复核。

## 遗留问题清单（建议修复）

1. **[P1] 候选「合并」无接口**：CandidateStatus.MERGED 是死状态。建议新增
   `POST /api/ai/candidates/{id}/merge`（参数 targetIssueId，校验反馈 ID 归属），把卡片转 MERGED
   并将反馈挂到目标 issue。
2. **[P2] 规则引擎跨模块误归组**：#79「报销单提交不了」被并入「发票上传失败」卡。
   可考虑跨模块命中时降低置信度/标记「疑似跨模块」供 PM 注意。
3. **[P3] `GET /api/ai/candidates` 无角色注解**：CS/DEV 也能看候选列表（只读）。
   若验收预期「仅 PM 可见」需补 `@PreAuthorize`；若产品定位是全员可见则保持现状。
4. **[P3] 无独立测试角色**：验收点 6 的「测试人员」由 DEV 承担（label=开发测试），如需
   职责分离需新增 TEST 角色并细化 PENDING_VERIFY 的流转权限。
5. **[P3] `POST /api/tasks` 仅 PM**：ADMIN 也不能代建任务（全系统唯一硬限 PM 的写接口），
   口径与 issue/AI 接口（PM+ADMIN）不一致，需产品确认是否故意设计。
